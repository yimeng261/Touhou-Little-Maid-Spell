package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.EnderPocketMaidSnapshotMessage;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 末影腰包服务类 - 统一管理所有enderPocket相关逻辑
 */
public class EnderPocketService {
    private static final long PREPARE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long SESSION_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final Map<UUID, RemoteSession> REMOTE_SESSIONS = new ConcurrentHashMap<>();

    /**
     * 末影腰包女仆信息
     */
    public record EnderPocketMaidInfo(UUID maidUUID, String maidName, int maidEntityId,
                                      float health, float maxHealth, int armor,
                                      double x, double y, double z, String dimension,
                                      boolean hasAnchorCore, String modelId) {}

    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(ServerPlayer player){
        return getPlayerEnderPocketMaids(player.getUUID(), player.getServer());
    }
    
    /**
     * 获取玩家所有装备末影腰包的女仆信息
     */
    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(UUID playerUUID) {
        return getPlayerEnderPocketMaids(playerUUID, null);
    }

    private static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(
            UUID playerUUID, @Nullable MinecraftServer expectedServer) {
        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(playerUUID);
        if (maids == null || maids.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnderPocketMaidInfo> enderPocketMaids = new ArrayList<>();
        
        for (EntityMaid maid : maids.values()) {
            if (maid != null && maid.isAlive() && !maid.isRemoved()
                    && isRegisteredServerEntity(maid, expectedServer)
                    && playerUUID.equals(maid.getOwnerUUID())
                    && BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)) {
                enderPocketMaids.add(new EnderPocketMaidInfo(
                        maid.getUUID(),
                        maid.getName().getString(),
                        maid.getId(),
                        maid.getHealth(),
                        maid.getMaxHealth(),
                        maid.getArmorValue(),
                        maid.getX(),
                        maid.getY(),
                        maid.getZ(),
                        maid.level().dimension().location().toString(),
                        BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE),
                        maid.getModelId()
                ));
            }
        }
        
        return enderPocketMaids;
    }
    
    /**
     * 打开女仆背包
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

    @Nullable
    public static Entity resolvePacketEntity(Level lookupLevel, int entityId) {
        Entity local = lookupLevel.getEntity(entityId);
        if (local instanceof EntityMaid || !(lookupLevel instanceof ServerLevel serverLevel)) {
            return local;
        }

        MinecraftServer server = serverLevel.getServer();
        for (RemoteSession session : REMOTE_SESSIONS.values()) {
            if (!session.active || session.server != server || session.entityId != entityId) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null || player.level() != lookupLevel) {
                continue;
            }
            EntityMaid maid = resolveRemoteMaid(player, entityId);
            if (maid != null) {
                return maid;
            }
        }
        return local;
    }

    public static boolean isRemoteSessionActive(ServerPlayer player, EntityMaid maid) {
        EntityMaid resolved = resolveRemoteMaid(player, maid.getId());
        return resolved == maid;
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
    }

    /** Teleports the owner beside a maid carrying both required baubles. */
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

    private static EntityMaid getOwnedEnderPocketMaid(ServerPlayer player, UUID maidUuid) {
        if (maidUuid == null) {
            return null;
        }
        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(player.getUUID());
        EntityMaid maid = maids == null ? null : maids.get(maidUuid);
        if (maid == null || maid.isRemoved() || !maid.isAlive()
                || !player.getUUID().equals(maid.getOwnerUUID()) || !maid.isOwnedBy(player)
                || !BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)
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

    private static boolean isRegisteredServerEntity(
            EntityMaid maid, @Nullable MinecraftServer expectedServer) {
        if (!(maid.level() instanceof ServerLevel level)
                || expectedServer != null && level.getServer() != expectedServer) {
            return false;
        }
        return level.getEntity(maid.getUUID()) == maid && level.getEntity(maid.getId()) == maid;
    }

    private static boolean sendRemoteSnapshot(
            ServerPlayer player, RemoteSession session, EntityMaid maid, boolean acknowledge) {
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new EnderPocketMaidSnapshotMessage(
                            session.sessionId, acknowledge, MaidEntityRestoreMessage.remoteSnapshot(maid))
            );
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
