package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

public final class FoxLeafOwnerEffectHelper {
    private FoxLeafOwnerEffectHelper() {
    }

    public static boolean canOwnerStandOnFluid(Player owner, FluidState fluidState) {
        TagKey<Fluid> fluidTag = fluidTagForState(fluidState);
        if (fluidTag == null || owner.isShiftKeyDown() || owner.isEyeInFluid(fluidTag)) {
            return false;
        }
        return findSupportingMaid(owner, fluidTag) != null;
    }

    public static boolean canApplyOwnerSurfaceMotion(Player owner, TagKey<Fluid> fluidTag) {
        return !owner.isShiftKeyDown() && !owner.isEyeInFluid(fluidTag);
    }

    public static boolean hasMoltenProtection(Player owner) {
        return findSupportingMaid(owner, FluidTags.LAVA) != null;
    }

    @Nullable
    public static EntityMaid findSupportingMaid(Player owner, TagKey<Fluid> fluidTag) {
        double range = rangeForFluid(fluidTag);
        for (EntityMaid maid : owner.level().getEntitiesOfClass(EntityMaid.class, owner.getBoundingBox().inflate(range))) {
            if (isValidSupportingMaid(owner, maid, fluidTag, range)) {
                return maid;
            }
        }
        return null;
    }

    public static boolean isValidSupportingMaid(Player owner, EntityMaid maid, TagKey<Fluid> fluidTag, double range) {
        if (!maid.isAlive() || owner.level() != maid.level()) {
            return false;
        }
        if (!isOwner(owner, maid)) {
            return false;
        }
        if (owner.distanceToSqr(maid) > range * range) {
            return false;
        }
        return hasMatchingFoxLeaf(maid, fluidTag);
    }

    public static boolean isOwner(Player owner, EntityMaid maid) {
        LivingEntity maidOwner = maid.getOwner();
        if (maidOwner == owner) {
            return true;
        }
        return maid.getOwnerUUID() != null && maid.getOwnerUUID().equals(owner.getUUID());
    }

    public static boolean hasMatchingFoxLeaf(EntityMaid maid, TagKey<Fluid> fluidTag) {
        if (fluidTag == FluidTags.WATER) {
            return ItemsUtil.hasBaubleItemInMaid(maid, MaidSpellItems.FLOATING_FOX_LEAF.get());
        }
        if (fluidTag == FluidTags.LAVA) {
            return ItemsUtil.hasBaubleItemInMaid(maid, MaidSpellItems.MOLTEN_FOX_LEAF.get());
        }
        return false;
    }

    public static double rangeForFluid(TagKey<Fluid> fluidTag) {
        if (fluidTag == FluidTags.WATER) {
            return Config.floatingFoxLeafOwnerRange;
        }
        if (fluidTag == FluidTags.LAVA) {
            return Config.moltenFoxLeafOwnerRange;
        }
        return 0.0D;
    }

    @Nullable
    private static TagKey<Fluid> fluidTagForState(FluidState fluidState) {
        if (fluidState.is(FluidTags.WATER)) {
            return FluidTags.WATER;
        }
        if (fluidState.is(FluidTags.LAVA)) {
            return FluidTags.LAVA;
        }
        return null;
    }
}
