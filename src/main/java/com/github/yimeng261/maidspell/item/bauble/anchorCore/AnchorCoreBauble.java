package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.player.ChunkLoadingData;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 锚定核心饰品逻辑
 * 为女仆提供全面保护，防止被其他模组影响
 */
public class AnchorCoreBauble implements IMaidBauble {
    private static final TicketController CHUNK_TICKET_CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "anchor_core"),
            AnchorCoreBauble::validateTickets);
    private static final int TICKET_CHECK_INTERVAL_TICKS = 20;

    /** 本次开服锚定核心挂着的全部持久票据：读档恢复时留下的，加上之后强加载的；释放时移除。 */
    private static final Set<AnchorTicket> ACTIVE_TICKETS = new HashSet<>();

    private final Map<UUID, Pair<ServerLevel, ChunkPos>> maidLastKnownChunkPos = new HashMap<>();

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(CHUNK_TICKET_CONTROLLER);
    }

    /**
     * 读档恢复持久票据前剔除能证明过期的票据：主人的锚定记录指向别的维度或区块时，这里的票据是女仆换区块后留下的旧票据。
     * 没有记录的票据先保留，留下的票据都交给周期校验，等区块和实体加载完再按女仆实际状态判定。
     */
    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        Map<UUID, ChunkLoadingData.LevelAndChunkPos> records = ChunkLoadingData.collectAll(level.getServer());
        helper.getEntityTickets().forEach((maidId, tickets) -> {
            ChunkLoadingData.LevelAndChunkPos record = records.get(maidId);
            boolean recordedHere = record != null && record.levelKey().equals(level.dimension());
            long recordedChunk = recordedHere ? ChunkPos.asLong(record.chunkX(), record.chunkZ()) : 0L;
            for (boolean ticking : new boolean[] {false, true}) {
                for (long chunk : ticking ? tickets.ticking() : tickets.nonTicking()) {
                    if (record != null && (!recordedHere || chunk != recordedChunk)) {
                        helper.removeTicket(maidId, chunk, ticking);
                    } else {
                        ACTIVE_TICKETS.add(new AnchorTicket(level.dimension(), maidId, chunk, ticking));
                    }
                }
            }
        });
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) {
            return;
        }
        AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
        anchoredEntityMaid.maidSpell$setAnchored(true);
        followMaid(maid);
    }

    /** 强加载跟着女仆走：换了维度或区块就把票据挪到她现在所在的区块，有主人时同步改记录。 */
    private void followMaid(EntityMaid maid) {
        ServerLevel serverLevel = (ServerLevel) maid.level();
        UUID maidId = maid.getUUID();
        var currentLevelAndChunkPos = Pair.of(serverLevel, maid.chunkPosition());
        var levelAndChunkPos = maidLastKnownChunkPos.get(maidId);
        if (currentLevelAndChunkPos.equals(levelAndChunkPos)) {
            return;
        }
        if (levelAndChunkPos != null) {
            maidLastKnownChunkPos.remove(maidId);
            Global.LOGGER.debug("取消加载女仆 {} 上次停留区块 {}", maidId, levelAndChunkPos.second());
            setChunkForced(maidId, levelAndChunkPos, false);
        }
        Global.LOGGER.debug("强制加载女仆 {} 位置更新区块 {}", maidId, maid.chunkPosition());
        setChunkForced(maidId, currentLevelAndChunkPos, true);
        maidLastKnownChunkPos.put(maidId, currentLevelAndChunkPos);
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            // 无主女仆没有记录，重启后靠持久票据和周期校验
            return;
        }
        // 不论主人在不在线都跟着女仆转移强加载，主人离线时记录先存为待变更
        MinecraftServer server = serverLevel.getServer();
        var record = new ChunkLoadingData.LevelAndChunkPos(serverLevel.dimension(),
                maid.chunkPosition().x, maid.chunkPosition().z);
        if (levelAndChunkPos == null) {
            releaseStaleRecordedChunk(server, ownerId, maidId, record);
        }
        ChunkLoadingData.updateRecord(server, ownerId, maidId, record);
    }

    /**
     * 重启或女仆重新进入世界后的第一次 tick：强加载还挂在记录指向的区块上（读档恢复的持久票据），
     * 记录与女仆实际所在区块不同时取消那里的强加载。
     */
    private void releaseStaleRecordedChunk(MinecraftServer server, UUID ownerId, UUID maidId,
                                           ChunkLoadingData.LevelAndChunkPos current) {
        ChunkLoadingData.LevelAndChunkPos recorded = ChunkLoadingData.getRecord(server, ownerId, maidId);
        ServerLevel recordedLevel = recorded == null || recorded.equals(current) ? null : server.getLevel(recorded.levelKey());
        if (recordedLevel != null) {
            Global.LOGGER.debug("取消加载女仆 {} 记录中的区块 {}", maidId, recorded);
            setChunkForced(maidId, Pair.of(recordedLevel, new ChunkPos(recorded.chunkX(), recorded.chunkZ())), false);
        }
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) {
            return;
        }
        AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
        anchoredEntityMaid.maidSpell$setAnchored(false);
        releaseChunkLoading(maid);
    }

    public static void disableChunkLoading(EntityMaid maid) {
        IMaidBauble bauble = BaubleManager.getBauble(MaidSpellItems.ANCHOR_CORE);
        if (bauble instanceof AnchorCoreBauble anchorCoreBauble) {
            anchorCoreBauble.releaseChunkLoading(maid);
        }
    }

    private void releaseChunkLoading(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }

        UUID ownerId = maid.getOwnerUUID();
        if (ownerId != null && maid.getServer() != null) {
            ChunkLoadingData.removeRecord(maid.getServer(), ownerId, maid.getUUID());
        }

        var levelAndChunkPos = maidLastKnownChunkPos.remove(maid.getUUID());
        if (levelAndChunkPos != null) {
            Global.LOGGER.debug("取消加载女仆 {} 强加载区块 {}", maid.getUUID(), levelAndChunkPos.second());
            setChunkForced(maid.getUUID(), levelAndChunkPos, false);
        }
    }

    private void setChunkForced(UUID maidId, Pair<ServerLevel, ChunkPos> levelAndChunkPos, boolean add) {
        ServerLevel level = levelAndChunkPos.first();
        setChunkForced(level, new AnchorTicket(level.dimension(), maidId, levelAndChunkPos.second().toLong(), true), add);
    }

    private static void setChunkForced(ServerLevel level, AnchorTicket ticket, boolean add) {
        CHUNK_TICKET_CONTROLLER.forceChunk(level, ticket.maidId(),
                ChunkPos.getX(ticket.chunk()), ChunkPos.getZ(ticket.chunk()), add, ticket.ticking());
        if (add) {
            ACTIVE_TICKETS.add(ticket);
        } else {
            ACTIVE_TICKETS.remove(ticket);
        }
    }

    /** 锚定核心是否正用这只女仆的票据强加载该区块。 */
    public static boolean hasTicket(ResourceKey<Level> dimension, UUID maidId, ChunkPos chunk) {
        return ACTIVE_TICKETS.contains(new AnchorTicket(dimension, maidId, chunk.toLong(), true))
                || ACTIVE_TICKETS.contains(new AnchorTicket(dimension, maidId, chunk.toLong(), false));
    }

    /**
     * 每 {@value #TICKET_CHECK_INTERVAL_TICKS} tick 校验一遍所有票据，不依赖女仆自己 tick：
     * <ul>
     * <li>女仆在票据所在维度已加载、活着、戴着锚定核心：仍在票据区块就保留；换了区块就先把强加载挪过去，再释放旧票据。</li>
     * <li>女仆在该维度已加载，但已死亡、已移除或没戴锚定核心：释放票据，删掉指向这里的记录。</li>
     * <li>该维度找不到女仆：票据区块和区块里的实体都加载完才判定她不在，释放票据；她在别的维度戴着锚定核心时记录由她自己的 tick 改写，
     * 否则删掉指向这里的记录。区块或实体还没加载完就等下一次校验。</li>
     * </ul>
     * 判定不看主人在不在线：离线主人的锚定女仆仍在强加载区块里，会被判为有效。
     */
    public static void validateActiveTickets(MinecraftServer server) {
        if (ACTIVE_TICKETS.isEmpty() || server.getTickCount() % TICKET_CHECK_INTERVAL_TICKS != 0
                || !(BaubleManager.getBauble(MaidSpellItems.ANCHOR_CORE) instanceof AnchorCoreBauble anchorCoreBauble)) {
            return;
        }
        for (AnchorTicket ticket : new ArrayList<>(ACTIVE_TICKETS)) {
            try {
                anchorCoreBauble.validateTicket(server, ticket);
            } catch (Exception e) {
                Global.LOGGER.warn("校验女仆 {} 的锚定票据失败，下次校验重试", ticket.maidId(), e);
            }
        }
    }

    private void validateTicket(MinecraftServer server, AnchorTicket ticket) {
        ServerLevel level = server.getLevel(ticket.dimension());
        if (level == null || !ACTIVE_TICKETS.contains(ticket)) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(ticket.chunk());
        Entity entity = level.getEntity(ticket.maidId());
        if (entity == null && !(level.hasChunk(chunkPos.x, chunkPos.z) && level.areEntitiesLoaded(ticket.chunk()))) {
            return;
        }
        if (entity instanceof EntityMaid maid && isAnchored(maid)) {
            if (maid.chunkPosition().equals(chunkPos)) {
                return;
            }
            followMaid(maid);
            if (ACTIVE_TICKETS.contains(ticket)) {
                Global.LOGGER.debug("女仆 {} 已不在锚定区块 {}，释放票据", ticket.maidId(), chunkPos);
                releaseTicket(level, ticket);
            }
            return;
        }
        Global.LOGGER.debug("女仆 {} 不在或已失效，释放锚定区块 {} 的票据", ticket.maidId(), chunkPos);
        releaseTicket(level, ticket);
        if (!isAnchoredAnywhere(server, ticket.maidId())) {
            removeRecordsPointingAt(server, ticket.maidId(),
                    new ChunkLoadingData.LevelAndChunkPos(ticket.dimension(), chunkPos.x, chunkPos.z));
        }
    }

    private void releaseTicket(ServerLevel level, AnchorTicket ticket) {
        maidLastKnownChunkPos.remove(ticket.maidId(), Pair.of(level, new ChunkPos(ticket.chunk())));
        setChunkForced(level, ticket, false);
    }

    private static boolean isAnchored(EntityMaid maid) {
        return !maid.isRemoved() && maid.isAlive() && BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE);
    }

    private static boolean isAnchoredAnywhere(MinecraftServer server, UUID maidId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(maidId) instanceof EntityMaid maid && isAnchored(maid)) {
                return true;
            }
        }
        return false;
    }

    /** 删掉指向该位置的记录；已指向别处的记录说明女仆换了位置，不动。主人离线时记为待删。 */
    private static void removeRecordsPointingAt(MinecraftServer server, UUID maidId,
                                                ChunkLoadingData.LevelAndChunkPos location) {
        ChunkLoadingData.collectAllByOwner(server).forEach((ownerId, maids) -> {
            if (location.equals(maids.get(maidId))) {
                ChunkLoadingData.removeRecord(server, ownerId, maidId);
            }
        });
    }

    public static void clearRuntimeCache() {
        IMaidBauble bauble = BaubleManager.getBauble(MaidSpellItems.ANCHOR_CORE);
        if (bauble instanceof AnchorCoreBauble anchorCoreBauble) {
            anchorCoreBauble.maidLastKnownChunkPos.clear();
        }
        ACTIVE_TICKETS.clear();
        ChunkLoadingData.clearSessionCache();
    }

    public static boolean isCallerAllowed(String className) {
        return className.startsWith("net.minecraft") ||
                className.startsWith("net.neoforged") ||
                className.startsWith("java") ||
                className.startsWith("jdk.") ||
                className.startsWith("sun.reflect") ||
                className.startsWith("it.unimi.dsi") ||
                className.startsWith("com.github.tartaricacid") ||
                className.startsWith("com.github.yimeng261") ||
                className.startsWith("com.google") ||
                className.startsWith("com.mojang") ||
                className.startsWith("io.redspace.ironsspellbooks") ||
                className.startsWith("whocraft.tardis_refined") ||
                className.startsWith("top.theillusivec4.curios") ||
                className.startsWith("tschipp.carryon") ||
                className.contains("backup") ||
                className.contains("maid") ||
                className.contains("c2me");
    }

    public static void clearCompound(CompoundTag compound) {
        for (String key : new java.util.ArrayList<>(compound.getAllKeys())) {
            compound.remove(key);
        }
    }

    /**
     * 检查调用栈，返回第一个不在白名单中的类名，若全部合法则返回 null。
     */
    public static String findIllegalCaller() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (!isCallerAllowed(e.getClassName())) {
                return e.getClassName();
            }
        }
        return null;
    }

    /** 锚定核心的一张持久票据：以女仆 UUID 为所有者强加载某维度的某个区块。 */
    private record AnchorTicket(ResourceKey<Level> dimension, UUID maidId, long chunk, boolean ticking) {
    }
}
