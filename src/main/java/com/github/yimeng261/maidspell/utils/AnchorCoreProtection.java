package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Shared decision layer for anchor core entity protections.
 */
public final class AnchorCoreProtection {
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
        if (!hasAnchorCore(maid)) {
            return false;
        }

        String illegalCaller = AnchorCoreBauble.findIllegalCaller();
        if (illegalCaller == null) {
            return false;
        }

        AnchorCoreBauble.clearCompound(compound);
        Global.LOGGER.warn("[MaidSpell] Illegal {} called for {} by {} (anchor_core protection)",
                method, maid.getUUID(), illegalCaller);
        return true;
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

    private static boolean isMaidRemoveCallAllowed(Entity.RemovalReason reason) {
        if (MaidHardRemovalProtection.isForcingProtectionCheck()) {
            return false;
        }

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = stackTrace.length - 10; i >= 0; i--) {
            String className = stackTrace[i].getClassName();
            if (className.endsWith("EntityMaid")) {
                continue;
            }
            if (AnchorCoreBauble.isCallerAllowed(className)) {
                return true;
            }
            if (reason == Entity.RemovalReason.CHANGED_DIMENSION
                    && className.startsWith("whocraft.tardis_refined")) {
                return true;
            }
        }
        return false;
    }
}
