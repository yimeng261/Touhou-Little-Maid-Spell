package com.github.yimeng261.maidspell.item.bauble.moltenFoxLeaf;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import com.github.yimeng261.maidspell.util.FoxLeafSurfaceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * 为岩浆行走提供稳定支撑，同时持续清理燃烧状态。
 */
public class MoltenFoxLeafBauble implements IMaidBauble {
    private static final double MAX_SINK_SPEED = -0.05D;
    private static final double SURFACE_FLOAT_SPEED = 0.08D;
    private static final double MIN_HORIZONTAL_SPEED_SQR = 0.0025D;
    // Keep the maid just above the lava surface so ground pathing can stay smooth
    // instead of falling back to lava-swimming movement.
    private static final double SURFACE_Y_OFFSET = 1.0D;
    private static final int IDLE_TRAIL_INTERVAL = 12;

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide() || maid.isPassenger() || maid.isUnderWater()) {
            return;
        }

        if (maid.isOnFire()) {
            maid.clearFire();
        }

        BlockPos blockPos = maid.blockPosition();
        FluidState feetFluid = maid.level().getFluidState(blockPos);
        FluidState belowFluid = maid.level().getFluidState(blockPos.below());
        if (!feetFluid.is(FluidTags.LAVA) && !belowFluid.is(FluidTags.LAVA)) {
            return;
        }

        maid.fallDistance = 0.0F;
        Vec3 deltaMovement = maid.getDeltaMovement();
        keepMaidAtLavaSurface(maid, blockPos, feetFluid, belowFluid, deltaMovement);
        deltaMovement = maid.getDeltaMovement();

        ensureMoltenLeafTrailUnderMaid(maid);
        if (deltaMovement.horizontalDistanceSqr() >= MIN_HORIZONTAL_SPEED_SQR
            && maid.tickCount % 2 == 0
            && maid.getRandom().nextFloat() < 0.55F) {
            spawnMoltenLeafTrail(maid);
        }
    }

    private static void keepMaidAtLavaSurface(EntityMaid maid, BlockPos blockPos, FluidState feetFluid,
                                              FluidState belowFluid, Vec3 deltaMovement) {
        FoxLeafSurfaceHelper.keepMaidAtFluidSurface(
            maid, blockPos, feetFluid, belowFluid, deltaMovement, FluidTags.LAVA,
            MAX_SINK_SPEED, SURFACE_FLOAT_SPEED, SURFACE_Y_OFFSET
        );
    }

    private static void spawnMoltenLeafTrail(EntityMaid maid) {
        Level level = maid.level();
        BlockPos centerFluidPos = FoxLeafSurfaceHelper.findSurfaceFluidPos(level, maid.blockPosition(), FluidTags.LAVA);
        if (centerFluidPos != null && FoxLeafSurfaceHelper.tryPlaceTrail(
            level, centerFluidPos.above(), true, FluidTags.LAVA,
            MaidSpellBlocks::isMoltenFoxLeafTrail,
            () -> MaidSpellBlocks.getRandomMoltenFoxLeafTrail(level.random))) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            double offsetX = (maid.getRandom().nextDouble() - 0.5D) * maid.getBbWidth() * 1.2D;
            double offsetZ = (maid.getRandom().nextDouble() - 0.5D) * maid.getBbWidth() * 1.2D;
            BlockPos fluidPos = FoxLeafSurfaceHelper.findSurfaceFluidPos(
                level,
                BlockPos.containing(maid.getX() + offsetX, maid.getY(), maid.getZ() + offsetZ),
                FluidTags.LAVA
            );
            if (fluidPos != null && FoxLeafSurfaceHelper.tryPlaceTrail(
                level, fluidPos.above(), maid.getRandom().nextFloat() < 0.35F, FluidTags.LAVA,
                MaidSpellBlocks::isMoltenFoxLeafTrail,
                () -> MaidSpellBlocks.getRandomMoltenFoxLeafTrail(level.random))) {
                return;
            }
        }
    }

    private static void ensureMoltenLeafTrailUnderMaid(EntityMaid maid) {
        Level level = maid.level();
        BlockPos fluidPos = FoxLeafSurfaceHelper.findSurfaceFluidPos(level, maid.blockPosition(), FluidTags.LAVA);
        if (fluidPos == null) {
            return;
        }
        BlockPos trailPos = fluidPos.above();
        FoxLeafSurfaceHelper.tryPlaceTrail(
            level, trailPos, false, FluidTags.LAVA,
            MaidSpellBlocks::isMoltenFoxLeafTrail,
            () -> MaidSpellBlocks.getRandomMoltenFoxLeafTrail(level.random)
        );
    }
}
