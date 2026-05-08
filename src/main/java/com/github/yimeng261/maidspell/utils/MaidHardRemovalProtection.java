package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic hard-removal guard for anchored maids.
 *
 * <p>This deliberately works on vanilla entity removal primitives instead of
 * checking any external mod or entity id.
 */
public final class MaidHardRemovalProtection {
    private static final int PERIODIC_CHECK_INTERVAL_TICKS = 20;
    private static final int WARN_INTERVAL_TICKS = 100;

    private static final Map<UUID, ProtectedMaidSnapshot> TRACKED_MAIDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING_RESTORE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WARN_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> RECOVERED_MAIDS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> ALLOW_HARD_REMOVAL = ThreadLocal.withInitial(() -> false);
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
        if (AnchorCoreBauble.findIllegalCaller() == null) {
            return false;
        }

        rememberProtected(maid);
        markPendingRestore(maid);
        logBlockedAttempt(maid, reason, "setRemoved");
        return true;
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
        if (AnchorCoreBauble.findIllegalCaller() == null) {
            forget(maid);
            return false;
        }

        rememberProtected(maid);
        markPendingRestore(maid);
        logBlockedAttempt(maid, maid.getRemovalReason(), "leave-level");
        return true;
    }

    public static void tick(MinecraftServer server) {
        serverTickCounter++;
        boolean periodicCheck = serverTickCounter % PERIODIC_CHECK_INTERVAL_TICKS == 0;
        if (!periodicCheck && PENDING_RESTORE.isEmpty()) {
            return;
        }

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
                ItemStack toInsert = soulSpell.copy();
                if (owner.getInventory().add(toInsert) && toInsert.isEmpty()) {
                    owner.containerMenu.broadcastChanges();
                    owner.sendSystemMessage(Component.literal("MaidSpell: 已拦截异常删除，女仆已回收到魂符并放入你的背包。"));
                    logRecovered(maid, "owner inventory");
                } else if (placeRecoveryChest(level, owner.blockPosition(), soulSpell)) {
                    owner.sendSystemMessage(Component.literal("MaidSpell: 已拦截异常删除；你的背包已满，女仆魂符已放入附近箱子。"));
                    logRecovered(maid, "recovery chest near owner");
                } else {
                    dropSoulSpell(level, owner.blockPosition(), soulSpell);
                    owner.sendSystemMessage(Component.literal("MaidSpell: 已拦截异常删除；背包已满且无法放置箱子，女仆魂符已掉落在你附近。"));
                    logRecovered(maid, "item drop near owner");
                }
            } else {
                BlockPos fallbackPos = BlockPos.containing(maid.position());
                if (placeRecoveryChest(level, fallbackPos, soulSpell)) {
                    logRecovered(maid, "recovery chest near last maid position");
                } else {
                    dropSoulSpell(level, fallbackPos, soulSpell);
                    logRecovered(maid, "item drop near last maid position");
                }
            }

            discardRecoveredMaid(maid);
            Global.updateMaidInfo(maid, false);
            TRACKED_MAIDS.remove(maidId, snapshot);
            PENDING_RESTORE.remove(maidId);
            LAST_WARN_TICK.remove(maidId);
            RECOVERED_MAIDS.remove(maidId);
        } catch (Exception e) {
            RECOVERED_MAIDS.remove(maidId);
            Global.LOGGER.error("[MaidSpell] Failed to recover anchored maid {} as soul spell after hard removal", maidId, e);
        }
    }

    private static void discardRecoveredMaid(EntityMaid maid) {
        runAllowingHardRemoval(maid::discard);
    }

    private static ItemStack createSoulSpell(EntityMaid maid) {
        ItemStack soulSpell = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(soulSpell, maid);
        return soulSpell;
    }

    private static boolean placeRecoveryChest(ServerLevel level, BlockPos origin, ItemStack soulSpell) {
        BlockPos chestPos = findChestPos(level, origin);
        if (chestPos == null) {
            return false;
        }

        Clearable.tryClear(level.getBlockEntity(chestPos));
        if (!level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)) {
            return false;
        }

        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(13, soulSpell.copy());
            chest.setChanged();
            return true;
        }
        return false;
    }

    private static BlockPos findChestPos(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int baseY = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, origin.getY()));
        for (int radius = 0; radius <= 4; radius++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        mutablePos.set(origin.getX() + dx, baseY + dy, origin.getZ() + dz);
                        if (level.isInWorldBounds(mutablePos) && level.getBlockState(mutablePos).canBeReplaced()) {
                            return mutablePos.immutable();
                        }
                    }
                }
            }
        }
        return null;
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
    }

    private static void forget(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        UUID maidId = maid.getUUID();
        TRACKED_MAIDS.remove(maidId);
        PENDING_RESTORE.remove(maidId);
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
}
