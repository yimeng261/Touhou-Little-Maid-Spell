package com.github.yimeng261.maidspell.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.block.custom.TransientFoxLeafTrailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class FoxLeafSurfaceHelper {
    private FoxLeafSurfaceHelper() {
    }

    public static void keepMaidAtFluidSurface(EntityMaid maid, BlockPos blockPos, FluidState feetFluid,
                                              FluidState belowFluid, Vec3 deltaMovement, TagKey<Fluid> fluidTag,
                                              double maxSinkSpeed, double surfaceFloatSpeed,
                                              double surfaceYOffset) {
        Level level = maid.level();
        BlockPos fluidPos = resolveSurfaceFluidPos(blockPos, feetFluid, belowFluid, fluidTag);
        if (fluidPos == null) {
            if (deltaMovement.y < maxSinkSpeed) {
                maid.setDeltaMovement(deltaMovement.x, maxSinkSpeed, deltaMovement.z);
            }
            return;
        }

        CollisionContext collisionContext = CollisionContext.of(maid);
        if (collisionContext.isAbove(LiquidBlock.STABLE_SHAPE, fluidPos, true)) {
            maid.setOnGround(true);
        }

        double surfaceY = fluidPos.getY() + surfaceYOffset;
        if (Math.abs(maid.getY() - surfaceY) > 0.02D) {
            maid.setPos(maid.getX(), surfaceY, maid.getZ());
            maid.setOnGround(true);
            maid.setDeltaMovement(deltaMovement.x, Math.max(deltaMovement.y, 0.0D), deltaMovement.z);
            return;
        }

        if (deltaMovement.y < 0.0D) {
            maid.setDeltaMovement(deltaMovement.x, Math.max(deltaMovement.y, surfaceFloatSpeed), deltaMovement.z);
        }
    }

    @Nullable
    public static BlockPos findSurfaceFluidPos(BlockGetter level, BlockPos pos, TagKey<Fluid> fluidTag) {
        FluidState fluidAtPos = level.getFluidState(pos);
        if (fluidAtPos.is(fluidTag) && !level.getFluidState(pos.above()).is(fluidTag)) {
            return pos;
        }

        BlockPos below = pos.below();
        FluidState fluidBelow = level.getFluidState(below);
        if (fluidBelow.is(fluidTag) && !fluidAtPos.is(fluidTag)) {
            return below;
        }
        return null;
    }

    public static boolean isFluidSurface(BlockGetter level, BlockPos pos, TagKey<Fluid> fluidTag) {
        return findSurfaceFluidPos(level, pos, fluidTag) != null;
    }

    public static boolean tryPlaceTrail(Level level, BlockPos pos, boolean allowCenterFallback, TagKey<Fluid> fluidTag,
                                        Predicate<BlockState> isTrailState, Supplier<BlockState> trailStateSupplier) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TransientFoxLeafTrailBlock trailBlock && isTrailState.test(state)) {
            trailBlock.refreshLifetime(level, pos);
            return true;
        }
        if (canPlaceTrailAt(level, pos, fluidTag, isTrailState)) {
            level.setBlock(pos, trailStateSupplier.get(), 3);
            return true;
        }
        if (allowCenterFallback) {
            BlockPos below = pos.below();
            if (canPlaceTrailAt(level, below, fluidTag, isTrailState)) {
                level.setBlock(below, trailStateSupplier.get(), 3);
                return true;
            }
        }
        return false;
    }

    private static boolean canPlaceTrailAt(Level level, BlockPos pos, TagKey<Fluid> fluidTag,
                                           Predicate<BlockState> isTrailState) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !isTrailState.test(state)) {
            return false;
        }

        FluidState belowFluid = level.getFluidState(pos.below());
        return belowFluid.is(fluidTag) && belowFluid.isSource();
    }

    @Nullable
    private static BlockPos resolveSurfaceFluidPos(BlockPos blockPos, FluidState feetFluid, FluidState belowFluid,
                                                   TagKey<Fluid> fluidTag) {
        if (feetFluid.is(fluidTag)) {
            return blockPos;
        }
        if (belowFluid.is(fluidTag)) {
            return blockPos.below();
        }
        return null;
    }
}
