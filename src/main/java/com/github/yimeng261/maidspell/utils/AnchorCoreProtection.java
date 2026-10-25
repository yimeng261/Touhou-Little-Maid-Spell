package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.AnchorCoreWhitelist;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared decision layer for anchor core entity protections.
 */
public final class AnchorCoreProtection {
    private static final Set<String> VANILLA_PERSISTENCE_FRAMES = Set.of(
            "net.minecraft.world.level.chunk.storage.EntityStorage#storeEntities",
            "net.minecraft.world.entity.Entity#restoreFrom",
            "net.minecraft.server.players.PlayerList#save"
    );

    /**
     * 内部序列化豁免：恢复包在非法调用栈内构造，需绕开序列化守卫。
     * 按女仆身份匹配，仅当前线程生效；外部模组请使用 {@link AnchorCoreWhitelist}。
     */
    private static final ThreadLocal<EntityMaid> INTERNAL_SERIALIZATION_MAID = new ThreadLocal<>();

    /** 不可信 remove 诊断日志的限频（毫秒）。 */
    private static final long UNTRUSTED_REMOVE_WARN_INTERVAL_MS = 10_000L;
    private static final AtomicLong LAST_UNTRUSTED_REMOVE_WARN_MS = new AtomicLong();

    private AnchorCoreProtection() {
    }

    public static boolean hasAnchorCore(Entity entity) {
        return entity instanceof EntityMaid maid && hasAnchorCore(maid);
    }

    public static boolean hasAnchorCore(EntityMaid maid) {
        try {
            return maid != null && BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE);
        } catch (Exception e) {
            Global.LOGGER.error("[MaidSpell] Failed to check maid anchor_core state", e);
            return false;
        }
    }

    public static boolean shouldBlockSetRemoved(Entity entity, Entity.RemovalReason reason) {
        return MaidHardRemovalProtection.shouldBlockSetRemoved(entity, reason);
    }

    public static boolean shouldBlockMaidRemove(EntityMaid maid, Entity.RemovalReason reason, String path) {
        if (maid == null) {
            return false;
        }
        if (MaidHardRemovalProtection.isAllowingHardRemoval()) {
            return false;
        }
        if (!hasAnchorCore(maid)) {
            Global.LOGGER.debug("Maid {} does not have anchor_core, allowing removal", maid.getUUID());
            return false;
        }
        if (maid.getHealth() <= 0.0F) {
            return false;
        }
        if (isMaidRemoveCallAllowed(reason)) {
            return false;
        }

        Global.LOGGER.debug("Prevented non-TLM removal of maid {} with health {} (anchor_core protection)",
                maid.getUUID(), maid.getHealth());
        MaidHardRemovalProtection.handleBlockedRemoveCall(maid, reason, path);
        return true;
    }

    public static boolean shouldBlockMaidSerialization(EntityMaid maid, CompoundTag compound, String method) {
        if (isInternalSerializationAllowed(maid)) {
            return false;
        }
        if (!hasAnchorCore(maid)) {
            return false;
        }

        String illegalCaller = findIllegalCallerOutsideVanillaPersistence();
        if (illegalCaller == null) {
            return false;
        }

        AnchorCoreBauble.clearCompound(compound);
        Global.LOGGER.warn("[MaidSpell] Illegal {} called for {} by {} (anchor_core protection)",
                method, maid.getUUID(), illegalCaller);
        return true;
    }

    /**
     * 一次遍历调用栈，返回第一个不在白名单中的类名；全部合法，或处在原版的区块实体存盘、换维度复制、
     * 玩家下线时连同坐骑存盘中时返回 null。
     * 这些原版路径不会把女仆从世界里带走，即使栈上还有其他模组的帧（例如由管理面板触发的 save-all）也放行。
     */
    private static String findIllegalCallerOutsideVanillaPersistence() {
        return StackWalker.getInstance().walk(frames -> {
            String illegalCaller = null;
            for (Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame frame = it.next();
                String className = frame.getClassName();
                if (VANILLA_PERSISTENCE_FRAMES.contains(className + "#" + frame.getMethodName())) {
                    return null;
                }
                if (illegalCaller == null && !AnchorCoreBauble.isCallerAllowed(className)) {
                    illegalCaller = className;
                }
            }
            return illegalCaller;
        });
    }

    public static boolean shouldBlockEntitySerialization(Entity entity, CompoundTag compound, String method) {
        return entity instanceof EntityMaid maid && shouldBlockMaidSerialization(maid, compound, method);
    }

    public static boolean shouldBlockAliveAnchoredDrop(Entity entity) {
        return entity instanceof EntityMaid maid && hasAnchorCore(maid) && maid.getHealth() > 0.0F;
    }

    public static boolean shouldBlockConversion(Entity entity) {
        return entity instanceof EntityMaid maid && hasAnchorCore(maid);
    }

    public static boolean shouldBlockCapture(Entity entity) {
        return entity instanceof EntityMaid maid && hasAnchorCore(maid);
    }

    /**
     * 判断 remove() 是否来自可信代码：逐帧扫描，出现非白名单帧即拒绝。
     *
     * <p>CHANGED_DIMENSION 正常跨维，始终放行；UNLOADED_TO_CHUNK/UNLOADED_WITH_PLAYER
     * 在强加载下不应出现，按可疑移除严格扫描；其余原因（含 KILLED/DISCARDED）同样严格扫描。</p>
     *
     * @param stackTrace 调用栈快照（null 按不可信处理）
     * @param reason     本次移除原因
     */
    static boolean isRemoveCallAllowed(StackTraceElement[] stackTrace, Entity.RemovalReason reason) {
        if (MaidHardRemovalProtection.isForcingProtectionCheck()) {
            return false;
        }
        if (MaidHardRemovalProtection.isAllowingHardRemoval()) {
            return true;
        }
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            // 跨维是正常行为（原版/TARDIS 等），取消反而会让目标维度副本与原实体并存。
            logUntrustedRemoval(stackTrace, reason, false);
            return true;
        }

        String illegalCaller = firstIllegalCaller(stackTrace);
        if (illegalCaller != null && isUnloadReason(reason)) {
            logUntrustedRemoval(stackTrace, reason, true);
        }
        return illegalCaller == null;
    }

    private static boolean isUnloadReason(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER;
    }

    /** 返回第一个非白名单类名；空栈返回哨兵，按不可信处理。 */
    private static String firstIllegalCaller(StackTraceElement[] stackTrace) {
        if (stackTrace == null || stackTrace.length == 0) {
            return "<unknown>";
        }
        for (StackTraceElement frame : stackTrace) {
            String className = frame.getClassName();
            if (className == null) {
                return "<null>";
            }
            if (!AnchorCoreWhitelist.isCallerAllowed(className)) {
                return className;
            }
        }
        return null;
    }

    private static void logUntrustedRemoval(StackTraceElement[] stackTrace, Entity.RemovalReason reason,
                                            boolean blocked) {
        String illegalCaller = firstIllegalCaller(stackTrace);
        if (illegalCaller == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = LAST_UNTRUSTED_REMOVE_WARN_MS.get();
        if (now - last < UNTRUSTED_REMOVE_WARN_INTERVAL_MS
                || !LAST_UNTRUSTED_REMOVE_WARN_MS.compareAndSet(last, now)) {
            return;
        }
        if (blocked) {
            Global.LOGGER.warn("[MaidSpell] Blocked unload-type {} removal initiated by untrusted caller {}. "
                            + "Anchor core keeps the maid's chunk loaded; register compatible mods via AnchorCoreWhitelist.",
                    reason, illegalCaller);
        } else {
            Global.LOGGER.warn("[MaidSpell] Allowed {} initiated by untrusted caller {}. "
                            + "Anchor core does not intercept dimension changes; register compatible mods via AnchorCoreWhitelist.",
                    reason, illegalCaller);
        }
    }

    private static boolean isMaidRemoveCallAllowed(Entity.RemovalReason reason) {
        return isRemoveCallAllowed(Thread.currentThread().getStackTrace(), reason);
    }

    /** 临时允许指定女仆序列化；仅供本模组内部使用，外部模组请用 {@link AnchorCoreWhitelist}。 */
    public static void runWithInternalSerialization(EntityMaid maid, Runnable action) {
        EntityMaid previous = INTERNAL_SERIALIZATION_MAID.get();
        INTERNAL_SERIALIZATION_MAID.set(maid);
        try {
            action.run();
        } finally {
            if (previous == null) {
                INTERNAL_SERIALIZATION_MAID.remove();
            } else {
                INTERNAL_SERIALIZATION_MAID.set(previous);
            }
        }
    }

    private static boolean isInternalSerializationAllowed(EntityMaid maid) {
        return maid != null && INTERNAL_SERIALIZATION_MAID.get() == maid;
    }
}
