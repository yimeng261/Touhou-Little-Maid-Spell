package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.network.message.MaidClientRemovalGuardMessage;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Generic hard-removal guard for anchored maids.
 *
 * <p>This deliberately works on vanilla entity removal primitives instead of
 * checking any external mod or entity id.
 */
public final class MaidHardRemovalProtection {
    private static final int PERIODIC_CHECK_INTERVAL_TICKS = 20;
    private static final int WARN_INTERVAL_TICKS = 100;
    private static final int CLIENT_RESTORE_RESEND_TICKS = 10;

    private static final Map<UUID, ProtectedMaidSnapshot> TRACKED_MAIDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING_RESTORE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PENDING_CLIENT_RESTORE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WARN_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> RECOVERED_MAIDS = new ConcurrentHashMap<>();
    private static final Queue<PendingChestRecovery> PENDING_CHEST_RECOVERIES = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<Boolean> ALLOW_HARD_REMOVAL = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> FORCE_PROTECTION_CHECK = ThreadLocal.withInitial(() -> false);
    private static int serverTickCounter;

    private MaidHardRemovalProtection() {
    }

    public static boolean shouldBlockSetRemoved(Entity entity, Entity.RemovalReason reason) {
        if (!(entity instanceof EntityMaid maid)) {
            return false;
        }
        if (ALLOW_HARD_REMOVAL.get()) {
            return false;
        }
        if (!isProtectedMaid(maid) || !isHardRemovalReason(reason)) {
            return false;
        }
        if (!isForcingProtectionCheck() && AnchorCoreBauble.findIllegalCaller() == null) {
            return false;
        }

        rememberProtected(maid);
        markPendingRestore(maid);
        syncClientEntityRestore(maid);
        logBlockedAttempt(maid, reason, "setRemoved");
        return true;
    }

    public static boolean shouldBlockUntrustedHardRemoval(Entity entity, Entity.RemovalReason reason) {
        final boolean[] blocked = {false};
        runAsUntrustedHardRemoval(() -> blocked[0] = shouldBlockSetRemoved(entity, reason));
        return blocked[0];
    }

    public static boolean isProtectedAgainstHardRemoval(Entity entity) {
        return entity instanceof EntityMaid maid && isProtectedMaid(maid);
    }

    public static void handleBlockedRemoveCall(EntityMaid maid, Entity.RemovalReason reason, String path) {
        if (!isProtectedMaid(maid) || !isHardRemovalReason(reason)) {
            return;
        }

        rememberProtected(maid);
        markPendingRestore(maid);
        syncClientEntityRestore(maid);
        logBlockedAttempt(maid, reason, path);
    }

    public static void rememberProtected(EntityMaid maid) {
        TRACKED_MAIDS.put(maid.getUUID(), ProtectedMaidSnapshot.of(maid));
    }

    public static void rememberIfProtected(EntityMaid maid) {
        if (isProtectedMaid(maid)) {
            rememberProtected(maid);
        } else {
            forget(maid);
        }
    }

    public static boolean handleMaidLeaveLevel(EntityMaid maid) {
        if (!isProtectedMaid(maid) || !isHardRemovalReason(maid.getRemovalReason())) {
            forget(maid);
            return false;
        }
        if (ALLOW_HARD_REMOVAL.get()) {
            return false;
        }
        if (!isForcingProtectionCheck() && AnchorCoreBauble.findIllegalCaller() == null) {
            forget(maid);
            return false;
        }

        rememberProtected(maid);
        markPendingRestore(maid);
        syncClientEntityRestore(maid);
        logBlockedAttempt(maid, maid.getRemovalReason(), "leave-level");
        return true;
    }

    public static void tick(MinecraftServer server) {
        serverTickCounter++;
        processPendingChestRecoveries(server);
        boolean periodicCheck = serverTickCounter % PERIODIC_CHECK_INTERVAL_TICKS == 0;
        if (!periodicCheck && PENDING_RESTORE.isEmpty() && PENDING_CLIENT_RESTORE.isEmpty()) {
            return;
        }

        syncPendingClientRestores(server);

        for (Map.Entry<UUID, ProtectedMaidSnapshot> entry : TRACKED_MAIDS.entrySet()) {
            UUID maidId = entry.getKey();
            ProtectedMaidSnapshot snapshot = entry.getValue();
            EntityMaid maid = snapshot.maid();
            boolean forcedCheck = PENDING_RESTORE.remove(maidId) != null;
            if (!periodicCheck && !forcedCheck) {
                continue;
            }

            if (!isProtectedMaid(maid) || !(maid.level() instanceof ServerLevel level) || level.getServer() != server) {
                TRACKED_MAIDS.remove(maidId, snapshot);
                LAST_WARN_TICK.remove(maidId);
                RECOVERED_MAIDS.remove(maidId);
                continue;
            }

            if (needsRecovery(maid, level, forcedCheck)) {
                recoverMaidAsSoulSpell(snapshot, level, forcedCheck);
            }
        }
    }

    public static void clear() {
        TRACKED_MAIDS.clear();
        PENDING_RESTORE.clear();
        PENDING_CLIENT_RESTORE.clear();
        PENDING_CHEST_RECOVERIES.clear();
        LAST_WARN_TICK.clear();
        RECOVERED_MAIDS.clear();
        serverTickCounter = 0;
    }

    public static void runAllowingHardRemoval(Runnable action) {
        ALLOW_HARD_REMOVAL.set(true);
        try {
            action.run();
        } finally {
            ALLOW_HARD_REMOVAL.remove();
        }
    }

    public static void runAsUntrustedHardRemoval(Runnable action) {
        FORCE_PROTECTION_CHECK.set(true);
        try {
            action.run();
        } finally {
            FORCE_PROTECTION_CHECK.remove();
        }
    }

    public static boolean isForcingProtectionCheck() {
        return FORCE_PROTECTION_CHECK.get();
    }

    private static boolean isProtectedMaid(EntityMaid maid) {
        try {
            if (maid == null || maid.level().isClientSide()) {
                return false;
            }
            if (maid.getHealth() <= 0.0F || maid.isDeadOrDying()) {
                return false;
            }
            return BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE);
        } catch (Exception e) {
            Global.LOGGER.error("[MaidSpell] Failed to check anchored maid hard-removal protection", e);
            return false;
        }
    }

    private static boolean isHardRemovalReason(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED;
    }

    private static void syncClientEntityRestore(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }

        MaidEntityRestoreMessage message = createRestoreMessage(maid);
        double restoreRange = clientRestoreRange(maid);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(maid) <= restoreRange * restoreRange) {
                sendClientEntityRestore(player, maid, message);
            }
        }
    }

    private static void sendClientEntityRestore(ServerPlayer player, EntityMaid maid, MaidEntityRestoreMessage message) {
        try {
            ClientboundAddEntityPacket spawnPacket = createAddEntityPacket(maid);
            player.connection.send(spawnPacket);

            List<SynchedEntityData.DataValue<?>> entityData = maid.getEntityData().getNonDefaultValues();
            if (entityData != null && !entityData.isEmpty()) {
                player.connection.send(new ClientboundSetEntityDataPacket(maid.getId(), entityData));
            }
            player.connection.send(new ClientboundTeleportEntityPacket(maid));
        } catch (Exception e) {
            Global.LOGGER.warn("[MaidSpell] Failed to send vanilla maid restore packets maid={} player={}",
                    maid.getUUID(), player.getUUID(), e);
        }

        player.connection.send(message);
    }

    private static ClientboundAddEntityPacket createAddEntityPacket(EntityMaid maid) {
        Vec3 deltaMovement = maid.getDeltaMovement();
        return new ClientboundAddEntityPacket(
                maid.getId(),
                maid.getUUID(),
                maid.getX(),
                maid.getY(),
                maid.getZ(),
                maid.getXRot(),
                maid.getYRot(),
                maid.getType(),
                0,
                deltaMovement,
                maid.getYHeadRot()
        );
    }

    private static double clientRestoreRange(EntityMaid maid) {
        return Math.max(128.0D, maid.getType().clientTrackingRange() * 16.0D + 16.0D);
    }

    private static MaidEntityRestoreMessage createRestoreMessage(EntityMaid maid) {
        CompoundTag entityTag = new CompoundTag();
        try {
            maid.saveWithoutId(entityTag);
        } catch (Exception e) {
            Global.LOGGER.warn("[MaidSpell] Failed to serialize maid {} for client restore packet", maid.getUUID(), e);
            entityTag = new CompoundTag();
        }

        List<SynchedEntityData.DataValue<?>> entityData = maid.getEntityData().getNonDefaultValues();
        if (entityData == null) {
            entityData = Collections.emptyList();
        }

        return new MaidEntityRestoreMessage(
                maid.getId(),
                maid.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(maid.getType()),
                entityTag,
                entityData,
                maid.getX(),
                maid.getY(),
                maid.getZ(),
                maid.getYRot(),
                maid.getXRot()
        );
    }

    private static void syncPendingClientRestores(MinecraftServer server) {
        if (PENDING_CLIENT_RESTORE.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Integer> entry : PENDING_CLIENT_RESTORE.entrySet()) {
            UUID maidId = entry.getKey();
            ProtectedMaidSnapshot snapshot = TRACKED_MAIDS.get(maidId);
            if (snapshot == null) {
                PENDING_CLIENT_RESTORE.remove(maidId);
                continue;
            }

            EntityMaid maid = snapshot.maid();
            if (!isProtectedMaid(maid) || !(maid.level() instanceof ServerLevel level) || level.getServer() != server) {
                PENDING_CLIENT_RESTORE.remove(maidId);
                continue;
            }

            syncClientEntityRestore(maid);
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks <= 0) {
                PENDING_CLIENT_RESTORE.remove(maidId);
            } else {
                PENDING_CLIENT_RESTORE.replace(maidId, entry.getValue(), remainingTicks);
            }
        }
    }

    private static boolean needsRecovery(EntityMaid maid, ServerLevel level, boolean forcedCheck) {
        Entity.RemovalReason reason = maid.getRemovalReason();
        if (maid.isRemoved() && (forcedCheck || isHardRemovalReason(reason))) {
            return true;
        }

        Entity byUuid = level.getEntity(maid.getUUID());
        Entity byId = level.getEntity(maid.getId());
        return (forcedCheck || maid.isAddedToLevel()) && (byUuid != maid || byId != maid);
    }

    private static void recoverMaidAsSoulSpell(ProtectedMaidSnapshot snapshot, ServerLevel level, boolean forcedCheck) {
        EntityMaid maid = snapshot.maid();
        UUID maidId = maid.getUUID();
        if (RECOVERED_MAIDS.putIfAbsent(maidId, Boolean.TRUE) != null) {
            return;
        }

        try {
            if (maid.isRemoved()) {
                Entity.RemovalReason reason = maid.getRemovalReason();
                if (!forcedCheck && !isHardRemovalReason(reason)) {
                    RECOVERED_MAIDS.remove(maidId);
                    return;
                }
                maid.revive();
            }

            ItemStack soulSpell = createSoulSpell(maid);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(snapshot.ownerId());
            if (owner != null) {
                ServerLevel ownerLevel = owner.serverLevel();
                if (addSoulSpellToInventory(owner, soulSpell)) {
                    owner.sendSystemMessage(Component.literal("MaidSpell: 已拦截异常删除，女仆已回收到魂符并放入你的背包。"));
                    logRecovered(maid, "owner inventory");
                } else {
                    queueRecoveryChest(ownerLevel, owner.getUUID(), owner.blockPosition(), soulSpell);
                    owner.sendSystemMessage(Component.literal("MaidSpell: 已拦截异常删除；你的背包已满，女仆魂符将在下一刻放入附近恢复箱。"));
                    logRecovered(maid, "queued recovery chest near owner");
                }
            } else {
                BlockPos fallbackPos = BlockPos.containing(maid.position());
                queueRecoveryChest(level, null, fallbackPos, soulSpell);
                logRecovered(maid, "queued recovery chest near last maid position");
            }

            discardRecoveredMaid(maid);
            Global.updateMaidInfo(maid, false);
            TRACKED_MAIDS.remove(maidId, snapshot);
            PENDING_RESTORE.remove(maidId);
            PENDING_CLIENT_RESTORE.remove(maidId);
            LAST_WARN_TICK.remove(maidId);
            RECOVERED_MAIDS.remove(maidId);
        } catch (Exception e) {
            RECOVERED_MAIDS.remove(maidId);
            Global.LOGGER.error("[MaidSpell] Failed to recover anchored maid {} as soul spell after hard removal", maidId, e);
        }
    }

    private static void discardRecoveredMaid(EntityMaid maid) {
        allowClientRemoval(maid);
        runAllowingHardRemoval(maid::discard);
    }

    private static ItemStack createSoulSpell(EntityMaid maid) {
        ItemStack soulSpell = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(soulSpell, maid);
        return soulSpell;
    }

    private static boolean addSoulSpellToInventory(ServerPlayer owner, ItemStack soulSpell) {
        int freeSlot = owner.getInventory().getFreeSlot();
        if (freeSlot < 0) {
            return false;
        }

        owner.getInventory().setItem(freeSlot, soulSpell.copy());
        owner.getInventory().getItem(freeSlot).setPopTime(5);
        owner.getInventory().setChanged();
        owner.containerMenu.broadcastChanges();
        owner.inventoryMenu.broadcastChanges();
        return true;
    }

    private static void queueRecoveryChest(ServerLevel level, UUID ownerId, BlockPos origin, ItemStack soulSpell) {
        PENDING_CHEST_RECOVERIES.add(new PendingChestRecovery(level.dimension(), ownerId, origin.immutable(), soulSpell.copy()));
    }

    private static void processPendingChestRecoveries(MinecraftServer server) {
        PendingChestRecovery recovery;
        while ((recovery = PENDING_CHEST_RECOVERIES.poll()) != null) {
            ServerPlayer owner = recovery.ownerId() == null ? null : server.getPlayerList().getPlayer(recovery.ownerId());
            ServerLevel level = owner != null ? owner.serverLevel() : server.getLevel(recovery.levelKey());
            if (level == null) {
                continue;
            }

            BlockPos origin = owner != null ? owner.blockPosition() : recovery.origin();
            BlockPos chestPos = placeRecoveryChest(level, origin, recovery.soulSpell());
            if (chestPos != null) {
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal(String.format(
                            "MaidSpell: 女仆魂符已放入附近恢复箱：%d %d %d。",
                            chestPos.getX(), chestPos.getY(), chestPos.getZ()
                    )));
                }
            } else {
                dropSoulSpell(level, origin, recovery.soulSpell());
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("MaidSpell: 无法放置恢复箱，女仆魂符已掉落在你附近。"));
                }
            }
        }
    }

    private static BlockPos placeRecoveryChest(ServerLevel level, BlockPos origin, ItemStack soulSpell) {
        BlockPos chestPos = findChestPos(level, origin);
        if (chestPos == null) {
            Global.LOGGER.warn("[MaidSpell] Failed to find recovery chest position near {}", origin);
            return null;
        }

        Global.LOGGER.warn("[MaidSpell] Placing recovery chest at {} in {}", chestPos, level.dimension().location());
        BlockState chestState = Blocks.CHEST.defaultBlockState();
        if (!level.setBlock(chestPos, chestState, 3)) {
            Global.LOGGER.warn("[MaidSpell] Failed to place recovery chest at {} in {}", chestPos, level.dimension().location());
            return null;
        }

        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(13, soulSpell.copy());
            chest.setChanged();
            return chestPos;
        }
        Global.LOGGER.warn("[MaidSpell] Recovery chest block entity missing at {} in {}", chestPos, level.dimension().location());
        return null;
    }

    private static BlockPos findChestPos(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int baseY = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, origin.getY()));
        for (int radius = 1; radius <= 5; radius++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        mutablePos.set(origin.getX() + dx, baseY + dy, origin.getZ() + dz);
                        if (isValidRecoveryChestPos(level, mutablePos)) {
                            return mutablePos.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isValidRecoveryChestPos(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private static void allowClientRemoval(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }

        double range = clientRestoreRange(maid);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(maid) <= range * range) {
                player.connection.send(new MaidClientRemovalGuardMessage(maid.getId(), true));
            }
        }
    }

    private static void dropSoulSpell(ServerLevel level, BlockPos origin, ItemStack soulSpell) {
        BlockPos dropPos = level.isInWorldBounds(origin) ? origin : level.getSharedSpawnPos();
        net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                level,
                dropPos.getX() + 0.5D,
                dropPos.getY() + 0.5D,
                dropPos.getZ() + 0.5D,
                soulSpell.copy());
        itemEntity.setInvulnerable(true);
        itemEntity.setGlowingTag(true);
        level.addFreshEntity(itemEntity);
    }

    private static void markPendingRestore(EntityMaid maid) {
        PENDING_RESTORE.put(maid.getUUID(), Boolean.TRUE);
        PENDING_CLIENT_RESTORE.put(maid.getUUID(), CLIENT_RESTORE_RESEND_TICKS);
    }

    private static void forget(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        UUID maidId = maid.getUUID();
        TRACKED_MAIDS.remove(maidId);
        PENDING_RESTORE.remove(maidId);
        PENDING_CLIENT_RESTORE.remove(maidId);
        LAST_WARN_TICK.remove(maidId);
        RECOVERED_MAIDS.remove(maidId);
    }

    private static void logBlockedAttempt(EntityMaid maid, Entity.RemovalReason reason, String path) {
        if (shouldLog(maid)) {
            Global.LOGGER.warn("[MaidSpell] Blocked hard removal path={} maid={} reason={} (anchor_core protection)",
                    path, maid.getUUID(), reason);
        }
    }

    private static void logRecovered(EntityMaid maid, String destination) {
        if (shouldLog(maid)) {
            Global.LOGGER.warn("[MaidSpell] Recovered anchored maid {} as soul spell after hard removal attempt: {}",
                    maid.getUUID(), destination);
        }
    }

    private static boolean shouldLog(EntityMaid maid) {
        long gameTime = maid.level() instanceof ServerLevel level ? level.getGameTime() : 0L;
        Long lastWarnTick = LAST_WARN_TICK.get(maid.getUUID());
        if (lastWarnTick != null && gameTime - lastWarnTick < WARN_INTERVAL_TICKS) {
            return false;
        }
        LAST_WARN_TICK.put(maid.getUUID(), gameTime);
        return true;
    }

    private record ProtectedMaidSnapshot(EntityMaid maid, UUID ownerId) {
        private static ProtectedMaidSnapshot of(EntityMaid maid) {
            return new ProtectedMaidSnapshot(maid, maid.getOwnerUUID());
        }
    }

    private record PendingChestRecovery(ResourceKey<Level> levelKey, UUID ownerId, BlockPos origin, ItemStack soulSpell) {
    }
}
