package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.mixin.accessor.SynchedEntityDataAccessor;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidData;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidSnapshot;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 末影腰包服务类 - 统一管理所有enderPocket相关逻辑
 */
public class EnderPocketService {
    public static final int MAX_MAID_INFOS = 64;
    private static final long PREPARE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    /** 远程会话期间开着这只女仆的界面时，即使没有操作也每秒把她的同步数据推给客户端代理一次 */
    private static final int REMOTE_DATA_SYNC_INTERVAL_TICKS = 20;
    private static final long SESSION_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final Map<UUID, RemoteSession> REMOTE_SESSIONS = new ConcurrentHashMap<>();

    /**
     * 末影腰包女仆信息
     */
    public static class EnderPocketMaidInfo {
        public final UUID maidUUID;
        public final String maidName;
        public final ResourceKey<Level> levelKey;
        public final int maidEntityId;
        public final float health;
        public final float maxHealth;
        public final int armor;
        public final double x;
        public final double y;
        public final double z;
        public final boolean hasAnchorCore;
        public final String modelId;

        public static final int MAX_MAID_NAME_LENGTH = 64;
        public static final int MAX_RESOURCE_ID_LENGTH = 256;

        private static final StreamCodec<ByteBuf, String> NAME_CODEC = ByteBufCodecs.stringUtf8(MAX_MAID_NAME_LENGTH);
        private static final StreamCodec<ByteBuf, String> RESOURCE_ID_CODEC = ByteBufCodecs.stringUtf8(MAX_RESOURCE_ID_LENGTH);
        private static final StreamCodec<ByteBuf, ResourceKey<Level>> LEVEL_KEY_CODEC = ResourceKey.streamCodec(Registries.DIMENSION);

        public static final StreamCodec<ByteBuf, EnderPocketMaidInfo> STREAM_CODEC = StreamCodec.of(
                (buf, info) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, info.maidUUID);
                    NAME_CODEC.encode(buf, info.maidName);
                    LEVEL_KEY_CODEC.encode(buf, info.levelKey);
                    buf.writeInt(info.maidEntityId);
                    buf.writeFloat(info.health);
                    buf.writeFloat(info.maxHealth);
                    ByteBufCodecs.VAR_INT.encode(buf, info.armor);
                    buf.writeDouble(info.x);
                    buf.writeDouble(info.y);
                    buf.writeDouble(info.z);
                    buf.writeBoolean(info.hasAnchorCore);
                    RESOURCE_ID_CODEC.encode(buf, info.modelId);
                },
                buf -> new EnderPocketMaidInfo(
                        UUIDUtil.STREAM_CODEC.decode(buf),
                        NAME_CODEC.decode(buf),
                        LEVEL_KEY_CODEC.decode(buf),
                        buf.readInt(),
                        buf.readFloat(),
                        buf.readFloat(),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readBoolean(),
                        RESOURCE_ID_CODEC.decode(buf)
                )
        );

        public static final StreamCodec<ByteBuf, List<EnderPocketMaidInfo>> LIST_STREAM_CODEC =
                ByteBufCodecs.collection(ArrayList::new, STREAM_CODEC, MAX_MAID_INFOS);

        /**
         * 截到 {@link #LIST_STREAM_CODEC} 能编码的条数并复制为不可变列表。
         */
        public static List<EnderPocketMaidInfo> copyLimited(List<EnderPocketMaidInfo> infos) {
            return List.copyOf(infos.subList(0, Math.min(infos.size(), MAX_MAID_INFOS)));
        }

        public EnderPocketMaidInfo(UUID maidUUID, String maidName, ResourceKey<Level> levelKey, int maidEntityId,
                                   float health, float maxHealth, int armor, double x, double y, double z,
                                   boolean hasAnchorCore, String modelId) {
            this.maidUUID = maidUUID;
            this.maidName = truncate(maidName, MAX_MAID_NAME_LENGTH);
            this.levelKey = levelKey;
            this.maidEntityId = maidEntityId;
            this.health = health;
            this.maxHealth = maxHealth;
            this.armor = armor;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hasAnchorCore = hasAnchorCore;
            this.modelId = truncate(modelId, MAX_RESOURCE_ID_LENGTH);
        }

        public UUID getMaidUUID() {
            return maidUUID;
        }

        private static String truncate(String value, int maxLength) {
            if (value == null) {
                return "";
            }
            return value.length() <= maxLength ? value : value.substring(0, maxLength);
        }
    }


    /**
     * 获取玩家所有装备末影腰包的女仆信息
     */
    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(playerUUID);
        if (maids == null || maids.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnderPocketMaidInfo> enderPocketMaids = new ArrayList<>();

        for (EntityMaid maid : maids.values()) {
            if (isEnderPocketAccessible(maid, playerUUID)
                    && isRegisteredServerEntity(maid, player.getServer())) {
                enderPocketMaids.add(new EnderPocketMaidInfo(
                        maid.getUUID(),
                        maid.getName().getString(),
                        maid.level().dimension(),
                        maid.getId(),
                        maid.getHealth(),
                        maid.getMaxHealth(),
                        maid.getArmorValue(),
                        maid.getX(),
                        maid.getY(),
                        maid.getZ(),
                        BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE),
                        maid.getModelId()
                ));
            }
        }

        return enderPocketMaids;
    }

    /**
     * 打开女仆背包：先向客户端同步女仆代理，客户端确认后再打开 TLM 原生界面
     */
    public static boolean openMaidInventory(ServerPlayer player, UUID maidUuid) {
        EntityMaid maid = getOwnedEnderPocketMaid(player, maidUuid);
        if (maid == null || maid.isSleeping()) {
            Global.LOGGER.warn("[MaidSpell] Rejected Ender Pocket open player={} maid={}: target is unavailable",
                    player.getUUID(), maidUuid);
            return false;
        }

        UUID sessionId = UUID.randomUUID();
        long now = System.nanoTime();
        RemoteSession session = new RemoteSession(
                player.getServer(), player.getUUID(), maid.getUUID(), maid.getId(),
                sessionId, now + PREPARE_TIMEOUT_NANOS
        );
        REMOTE_SESSIONS.put(player.getUUID(), session);
        if (!sendRemoteSnapshot(player, session, maid, true)) {
            REMOTE_SESSIONS.remove(player.getUUID(), session);
            return false;
        }

        Global.LOGGER.debug("[MaidSpell] Prepared Ender Pocket remote session player={} maid={} entityId={} dimension={}",
                player.getUUID(), maid.getUUID(), maid.getId(), maid.level().dimension().location());
        return true;
    }

    public static boolean completeRemoteOpen(@Nullable ServerPlayer player, UUID sessionId) {
        if (player == null || sessionId == null) {
            return false;
        }
        RemoteSession session = REMOTE_SESSIONS.get(player.getUUID());
        long now = System.nanoTime();
        if (session == null || session.active || !session.sessionId.equals(sessionId)
                || session.isExpired(now)) {
            Global.LOGGER.warn("[MaidSpell] Rejected stale Ender Pocket ready response player={} session={}",
                    player.getUUID(), sessionId);
            return false;
        }

        EntityMaid maid = validateSessionMaid(player, session);
        if (maid == null) {
            REMOTE_SESSIONS.remove(player.getUUID(), session);
            return false;
        }

        session.activate(now + SESSION_TIMEOUT_NANOS);
        Global.LOGGER.debug("[MaidSpell] Activated Ender Pocket remote session player={} maid={} entityId={}",
                player.getUUID(), maid.getUUID(), maid.getId());
        maid.openMaidGui(player, TabIndex.MAIN);
        return true;
    }

    @Nullable
    public static EntityMaid resolveRemoteMaid(ServerPlayer player, int entityId) {
        RemoteSession session = REMOTE_SESSIONS.get(player.getUUID());
        long now = System.nanoTime();
        if (session == null || !session.active || session.entityId != entityId || session.isExpired(now)) {
            if (session != null && session.isExpired(now)) {
                REMOTE_SESSIONS.remove(player.getUUID(), session);
            }
            return null;
        }

        EntityMaid maid = validateSessionMaid(player, session);
        if (maid == null) {
            REMOTE_SESSIONS.remove(player.getUUID(), session);
            return null;
        }
        session.touch(now + SESSION_TIMEOUT_NANOS);
        if (session.markFallbackLogged()) {
            Global.LOGGER.debug("[MaidSpell] Resolved remote maid across level player={} maid={} entityId={}",
                    player.getUUID(), maid.getUUID(), entityId);
        }
        return maid;
    }

    /**
     * TLM 服务端数据包按实体 ID 查找女仆：本地找不到时回退到发送者自己已激活的远程会话。
     * 实体 ID 正是会话里那只女仆时，不论本地能否找到都续期会话；{@code changesMaid} 为 true 时还在本 tick 末
     * 把改动推给客户端代理（同维度但超出追踪范围的女仆不会被原版同步），只读的请求传 false。
     */
    @Nullable
    public static Entity resolvePacketEntity(@Nullable Entity local, int entityId, @Nullable Player sender,
                                             boolean changesMaid) {
        if (!(sender instanceof ServerPlayer player)) {
            return local;
        }
        EntityMaid maid = resolveRemoteMaid(player, entityId);
        if (maid == null || (local instanceof EntityMaid && local != maid)) {
            return local;
        }
        // 数据包处理完后在本 tick 末把改动推给客户端代理
        RemoteSession session = changesMaid ? REMOTE_SESSIONS.get(player.getUUID()) : null;
        if (session != null) {
            session.dataDirty = true;
        }
        return maid;
    }

    public static boolean isRemoteSessionActive(ServerPlayer player, EntityMaid maid) {
        EntityMaid resolved = resolveRemoteMaid(player, maid.getId());
        return resolved == maid;
    }

    /**
     * 装备末影腰包的女仆，界面有效与否不看距离：玩家是主人、女仆醒着且存活；
     * 服务端上女仆与玩家不在同一维度时，还要有玩家自己已激活的远程会话。
     */
    public static boolean isGuiStillValid(Player player, EntityMaid maid) {
        if (!maid.isOwnedBy(player) || maid.isSleeping() || !maid.isAlive()) {
            return false;
        }
        return !(player instanceof ServerPlayer serverPlayer) || maid.level() == serverPlayer.level()
                || isRemoteSessionActive(serverPlayer, maid);
    }

    public static void syncRemoteProxyBeforeMenu(ServerPlayer player, EntityMaid maid) {
        RemoteSession session = REMOTE_SESSIONS.get(player.getUUID());
        if (session == null || !session.active || session.entityId != maid.getId()
                || !session.maidId.equals(maid.getUUID())) {
            return;
        }
        EntityMaid resolved = resolveRemoteMaid(player, maid.getId());
        if (resolved == maid) {
            sendRemoteSnapshot(player, session, maid, false);
        }
    }

    public static void clearRemoteSession(ServerPlayer player) {
        REMOTE_SESSIONS.remove(player.getUUID());
    }

    public static void clearRemoteSessions(MinecraftServer server) {
        REMOTE_SESSIONS.entrySet().removeIf(entry -> entry.getValue().server == server);
    }

    public static void tickRemoteSessions(MinecraftServer server) {
        long now = System.nanoTime();
        REMOTE_SESSIONS.entrySet().removeIf(entry -> {
            RemoteSession session = entry.getValue();
            return session.server == server && (session.isExpired(now)
                    || server.getPlayerList().getPlayer(session.playerId) == null);
        });
        boolean periodic = server.getTickCount() % REMOTE_DATA_SYNC_INTERVAL_TICKS == 0;
        for (RemoteSession session : REMOTE_SESSIONS.values()) {
            if (session.server != server || !session.active || !(session.dataDirty || periodic)) {
                continue;
            }
            boolean dirty = session.dataDirty;
            session.dataDirty = false;
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            EntityMaid maid = player == null ? null : validateSessionMaid(player, session);
            if (maid == null) {
                REMOTE_SESSIONS.remove(session.playerId, session);
                continue;
            }
            if (!dirty && !(player.containerMenu instanceof AbstractMaidContainer menu && menu.getMaid() == maid)) {
                continue;
            }
            List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
            for (SynchedEntityData.DataItem<?> item : ((SynchedEntityDataAccessor) maid.getEntityData()).maidspell$getItemsById()) {
                values.add(item.value());
            }
            player.connection.send(new S2CEnderPocketMaidData(maid.getId(), values));
        }
    }

    /**
     * 把主人传送到同时佩戴末影腰包和锚定核心的女仆身边
     */
    public static boolean teleportToMaid(ServerPlayer player, UUID maidUuid) {
        EntityMaid maid = getOwnedEnderPocketMaid(player, maidUuid);
        if (maid == null || !BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)
                || !(maid.level() instanceof ServerLevel targetLevel)) {
            return false;
        }

        Vec3 destination = findSafeDestination(targetLevel, maid, player);
        clearRemoteSession(player);
        player.stopRiding();
        player.teleportTo(targetLevel, destination.x, destination.y, destination.z,
                player.getYRot(), player.getXRot());
        player.resetFallDistance();
        return true;
    }

    @Nullable
    private static EntityMaid getOwnedEnderPocketMaid(ServerPlayer player, UUID maidUuid) {
        if (maidUuid == null) {
            return null;
        }
        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(player.getUUID());
        EntityMaid maid = maids == null ? null : maids.get(maidUuid);
        if (maid == null
                || !isEnderPocketAccessible(maid, player.getUUID())
                || !maid.isOwnedBy(player)
                || !isRegisteredServerEntity(maid, player.getServer())) {
            return null;
        }
        return maid;
    }

    @Nullable
    private static EntityMaid validateSessionMaid(ServerPlayer player, RemoteSession session) {
        if (session.server != player.getServer() || !session.playerId.equals(player.getUUID())) {
            return null;
        }
        EntityMaid maid = getOwnedEnderPocketMaid(player, session.maidId);
        if (maid == null || maid.getId() != session.entityId || maid.isSleeping()) {
            Global.LOGGER.warn("[MaidSpell] Invalidated Ender Pocket remote session player={} maid={} entityId={}",
                    player.getUUID(), session.maidId, session.entityId);
            return null;
        }
        return maid;
    }

    /**
     * 女仆存活、归属该玩家且装备了末影腰包
     */
    private static boolean isEnderPocketAccessible(EntityMaid maid, UUID playerUUID) {
        return maid.isAlive()
                && playerUUID.equals(maid.getOwnerUUID())
                && BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET);
    }

    /**
     * 女仆仍登记在所属服务器维度的实体表里（排除已卸载或属于旧服务器的残留引用）
     */
    private static boolean isRegisteredServerEntity(EntityMaid maid, @Nullable MinecraftServer expectedServer) {
        if (!(maid.level() instanceof ServerLevel level)
                || expectedServer != null && level.getServer() != expectedServer) {
            return false;
        }
        return level.getEntity(maid.getUUID()) == maid && level.getEntity(maid.getId()) == maid;
    }

    private static boolean sendRemoteSnapshot(
            ServerPlayer player, RemoteSession session, EntityMaid maid, boolean acknowledge) {
        try {
            player.connection.send(new S2CEnderPocketMaidSnapshot(
                    session.sessionId, acknowledge, MaidEntityRestoreMessage.remoteSnapshot(maid)));
            return true;
        } catch (Exception e) {
            Global.LOGGER.warn("[MaidSpell] Failed to synchronize Ender Pocket maid proxy player={} maid={}",
                    player.getUUID(), maid.getUUID(), e);
            return false;
        }
    }

    private static Vec3 findSafeDestination(ServerLevel level, EntityMaid maid, ServerPlayer player) {
        BlockPos origin = maid.blockPosition();
        BlockPos[] candidates = {
                origin.relative(Direction.NORTH), origin.relative(Direction.SOUTH),
                origin.relative(Direction.WEST), origin.relative(Direction.EAST),
                origin.relative(Direction.NORTH).relative(Direction.WEST),
                origin.relative(Direction.NORTH).relative(Direction.EAST),
                origin.relative(Direction.SOUTH).relative(Direction.WEST),
                origin.relative(Direction.SOUTH).relative(Direction.EAST)
        };
        for (BlockPos pos : candidates) {
            if (isSafeStandingPosition(level, pos, player)) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return new Vec3(maid.getX(), maid.getY() + 0.25D, maid.getZ());
    }

    private static boolean isSafeStandingPosition(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }
        Vec3 target = Vec3.atBottomCenterOf(pos);
        return level.noCollision(player, player.getBoundingBox().move(target.subtract(player.position())));
    }

    private static final class RemoteSession {
        private final MinecraftServer server;
        private final UUID playerId;
        private final UUID maidId;
        private final int entityId;
        private final UUID sessionId;
        private volatile long expiresAtNanos;
        private volatile boolean active;
        private volatile boolean fallbackLogged;
        private volatile boolean dataDirty;

        private RemoteSession(MinecraftServer server, UUID playerId, UUID maidId, int entityId,
                              UUID sessionId, long expiresAtNanos) {
            this.server = server;
            this.playerId = playerId;
            this.maidId = maidId;
            this.entityId = entityId;
            this.sessionId = sessionId;
            this.expiresAtNanos = expiresAtNanos;
        }

        private boolean isExpired(long now) {
            return now >= expiresAtNanos;
        }

        private void activate(long expiresAtNanos) {
            this.active = true;
            this.expiresAtNanos = expiresAtNanos;
        }

        private void touch(long expiresAtNanos) {
            this.expiresAtNanos = expiresAtNanos;
        }

        private boolean markFallbackLogged() {
            if (fallbackLogged) {
                return false;
            }
            fallbackLogged = true;
            return true;
        }
    }
}
