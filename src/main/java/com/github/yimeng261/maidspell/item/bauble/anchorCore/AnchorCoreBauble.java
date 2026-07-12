package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.player.ChunkLoadingData;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 锚定核心饰品逻辑
 * 为女仆提供全面保护，防止被其他模组影响
 */
public class AnchorCoreBauble implements IMaidBauble {
    private static final TicketController CHUNK_TICKET_CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "anchor_core"));

    private final Map<UUID, Pair<ServerLevel, ChunkPos>> maidLastKnownChunkPos = new HashMap<>();

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(CHUNK_TICKET_CONTROLLER);
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) {
            return;
        }
        clearInvalidMaidChunk();

        AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
        anchoredEntityMaid.maidSpell$setAnchored(true);

        ServerLevel serverLevel = (ServerLevel) maid.level();
        ChunkLoadingData chunkLoadingData = Optional.ofNullable(maid.getOwner())
                .map(o -> o.getData(ChunkLoadingData.ATTACHMENT_TYPE))
                .orElse(null);
        var currentLevelAndChunkPos = Pair.of(serverLevel, maid.chunkPosition());
        var levelAndChunkPos = maidLastKnownChunkPos.get(maid.getUUID());
        if (!currentLevelAndChunkPos.equals(levelAndChunkPos)) {
            if (levelAndChunkPos != null) {
                maidLastKnownChunkPos.remove(maid.getUUID());
                Global.LOGGER.debug("取消加载女仆 {} 上次停留区块 {}", maid.getUUID(), levelAndChunkPos.second());
                setChunkForced(maid.getUUID(), levelAndChunkPos, false);
            }
            if (chunkLoadingData != null) {
                Global.LOGGER.debug("强制加载女仆 {} 位置更新区块 {}", maid.getUUID(), maid.chunkPosition());
                setChunkForced(maid.getUUID(), currentLevelAndChunkPos, true);
                maidLastKnownChunkPos.put(maid.getUUID(), currentLevelAndChunkPos);
                var chunkPosMap = chunkLoadingData.maidChunks();
                chunkPosMap.put(maid.getUUID(), new ChunkLoadingData.LevelAndChunkPos(serverLevel.dimension(), maid.chunkPosition().x, maid.chunkPosition().z));
            }
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

        Optional.ofNullable(maid.getOwner())
                .map(o -> o.getData(ChunkLoadingData.ATTACHMENT_TYPE))
                .ifPresent(data -> data.maidChunks().remove(maid.getUUID()));

        var levelAndChunkPos = maidLastKnownChunkPos.remove(maid.getUUID());
        if (levelAndChunkPos != null) {
            Global.LOGGER.debug("取消加载女仆 {} 强加载区块 {}", maid.getUUID(), levelAndChunkPos.second());
            setChunkForced(maid.getUUID(), levelAndChunkPos, false);
        }
    }

    private void setChunkForced(UUID maidId, Pair<ServerLevel, ChunkPos> levelAndChunkPos, boolean add) {
        ChunkPos chunkPos = levelAndChunkPos.second();
        CHUNK_TICKET_CONTROLLER.forceChunk(
                levelAndChunkPos.first(), maidId, chunkPos.x, chunkPos.z, add, true);
    }

    private void clearInvalidMaidChunk() {
        for (var iterator = maidLastKnownChunkPos.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var maidId = entry.getKey();
            var levelAndChunkPos = entry.getValue();
            var level = levelAndChunkPos.first();
            Entity entity = level.getEntity(maidId);
            if (entity == null) {
                iterator.remove();
                setChunkForced(maidId, levelAndChunkPos, false);
            }
        }
    }

    public static void clearRuntimeCache() {
        IMaidBauble bauble = BaubleManager.getBauble(MaidSpellItems.ANCHOR_CORE);
        if (bauble instanceof AnchorCoreBauble anchorCoreBauble) {
            anchorCoreBauble.maidLastKnownChunkPos.clear();
        }
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

}
