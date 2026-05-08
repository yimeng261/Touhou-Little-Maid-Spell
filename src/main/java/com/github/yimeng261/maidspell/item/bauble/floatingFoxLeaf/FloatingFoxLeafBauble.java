package com.github.yimeng261.maidspell.item.bauble.floatingFoxLeaf;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import com.github.yimeng261.maidspell.utils.FoxLeafOwnerEffectHelper;
import com.github.yimeng261.maidspell.utils.FoxLeafSurfaceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * 为水面行走提供额外稳定处理，避免女仆在水面边缘反复下沉。
 * 同时也让附近的主人获得水面行走能力。
 */
public class FloatingFoxLeafBauble implements IMaidBauble {
    private static final double MAX_SINK_SPEED = -0.05D;
    private static final double SURFACE_FLOAT_SPEED = 0.08D;
    private static final double MIN_HORIZONTAL_SPEED_SQR = 0.0025D;
    private static final double SURFACE_Y_OFFSET = 0.9D;

    private static final double SURFACE_Y_OFFSET_OWNER = 0.9D;

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide() || maid.isPassenger() || maid.isUnderWater()) {
            return;
        }

        BlockPos blockPos = maid.blockPosition();
        FluidState feetFluid = maid.level().getFluidState(blockPos);
        FluidState belowFluid = maid.level().getFluidState(blockPos.below());
        if (!feetFluid.is(FluidTags.WATER) && !belowFluid.is(FluidTags.WATER)) {
            applyToOwnerIfNearby(maid);
            return;
        }

        maid.fallDistance = 0.0F;
        Vec3 deltaMovement = maid.getDeltaMovement();
        keepMaidAtWaterSurface(maid, blockPos, feetFluid, belowFluid, deltaMovement);
        deltaMovement = maid.getDeltaMovement();

        ensureFloatingLeafTrailUnderMaid(maid);
        if (deltaMovement.horizontalDistanceSqr() >= MIN_HORIZONTAL_SPEED_SQR
                && maid.tickCount % 2 == 0
                && maid.getRandom().nextFloat() < 0.55F) {
            spawnFloatingLeafTrail(maid);
        }

        applyToOwnerIfNearby(maid);
    }

    @Override
    public boolean syncClient(EntityMaid maid, ItemStack baubleItem) {
        return true;
    }

    private static void keepMaidAtWaterSurface(EntityMaid maid, BlockPos blockPos, FluidState feetFluid,
                                               FluidState belowFluid, Vec3 deltaMovement) {
        FoxLeafSurfaceHelper.keepEntityAtFluidSurface(
                maid, blockPos, feetFluid, belowFluid, deltaMovement, FluidTags.WATER,
                MAX_SINK_SPEED, SURFACE_FLOAT_SPEED, SURFACE_Y_OFFSET
        );
    }

    /**
     * 如果主人附近且站在水上，赋予主人水面行走能力。
     */
    private static void applyToOwnerIfNearby(EntityMaid maid) {
        if (!(maid.getOwner() instanceof Player owner)
                || owner.level() != maid.level()
                || owner.isSpectator()
                || owner.isPassenger()) {
            return;
        }

        double range = Config.floatingFoxLeafOwnerRange;
        if (!FoxLeafOwnerEffectHelper.isValidSupportingMaid(owner, maid, FluidTags.WATER, range)) {
            return;
        }

        if (!FoxLeafOwnerEffectHelper.canApplyOwnerSurfaceMotion(owner, FluidTags.WATER)) {
            return;
        }

        BlockPos ownerPos = owner.blockPosition();
        FluidState feetFluid = owner.level().getFluidState(ownerPos);
        FluidState belowFluid = owner.level().getFluidState(ownerPos.below());

        if (!feetFluid.is(FluidTags.WATER) && !belowFluid.is(FluidTags.WATER)) {
            return;
        }

        owner.fallDistance = 0.0F;
        FoxLeafSurfaceHelper.keepEntityAtFluidSurface(
                owner, ownerPos, feetFluid, belowFluid,
                owner.getDeltaMovement(), FluidTags.WATER,
                MAX_SINK_SPEED, SURFACE_FLOAT_SPEED, SURFACE_Y_OFFSET_OWNER
        );

        ensureTrailUnderOwner(owner);
    }

    private static void ensureTrailUnderOwner(Player owner) {
        Level level = owner.level();
        BlockPos fluidPos = FoxLeafSurfaceHelper.findSurfaceFluidPos(level, owner.blockPosition(), FluidTags.WATER);
        if (fluidPos == null) {
            return;
        }
        FoxLeafSurfaceHelper.tryPlaceTrail(
                level, fluidPos.above(), false, FluidTags.WATER,
                MaidSpellBlocks::isFloatingFoxLeafTrail,
                () -> MaidSpellBlocks.getRandomFloatingFoxLeafTrail(level.random)
        );
    }

    private static void spawnFloatingLeafTrail(EntityMaid maid) {
        Level level = maid.level();
        BlockPos centerFluidPos = FoxLeafSurfaceHelper.findSurfaceFluidPos(level, maid.blockPosition(), FluidTags.WATER);
        if (centerFluidPos != null && FoxLeafSurfaceHelper.tryPlaceTrail(
                level, centerFluidPos.above(), true, FluidTags.WATER,
                MaidSpellBlocks::isFloatingFoxLeafTrail,
                () -> MaidSpellBlocks.getRandomFloatingFoxLeafTrail(level.random))) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            double offsetX = (maid.getRandom().nextDouble() - 0.5D) * maid.getBbWidth() * 1.2D;
            double offsetZ = (maid.getRandom().nextDouble() - 0.5D) * maid.getBbWidth() * 1.2D;
            BlockPos fluidPos = FoxLeafSurfaceHelper.findSurfaceFluidPos(
                    level,
                    BlockPos.containing(maid.getX() + offsetX, maid.getY(), maid.getZ() + offsetZ),
                    FluidTags.WATER
            );
            if (fluidPos != null && FoxLeafSurfaceHelper.tryPlaceTrail(
                    level, fluidPos.above(), maid.getRandom().nextFloat() < 0.35F, FluidTags.WATER,
                    MaidSpellBlocks::isFloatingFoxLeafTrail,
                    () -> MaidSpellBlocks.getRandomFloatingFoxLeafTrail(level.random))) {
                return;
            }
        }
    }

    private static void ensureFloatingLeafTrailUnderMaid(EntityMaid maid) {
        Level level = maid.level();
        BlockPos fluidPos = FoxLeafSurfaceHelper.findSurfaceFluidPos(level, maid.blockPosition(), FluidTags.WATER);
        if (fluidPos == null) {
            return;
        }
        BlockPos trailPos = fluidPos.above();
        FoxLeafSurfaceHelper.tryPlaceTrail(
                level, trailPos, false, FluidTags.WATER,
                MaidSpellBlocks::isFloatingFoxLeafTrail,
                () -> MaidSpellBlocks.getRandomFloatingFoxLeafTrail(level.random)
        );
    }
}
