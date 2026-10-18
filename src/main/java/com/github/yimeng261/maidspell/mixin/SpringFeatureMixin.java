package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.TerrainFeatureGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 涌泉（熔岩和水）不出现在本模组标记的结构占地里，见 {@link TerrainFeatureGuard}。
 * 只在条件都满足、真要放泉眼时才查结构；没放下的泉眼随后的流体刻是空操作。
 */
@Mixin(SpringFeature.class)
public abstract class SpringFeatureMixin {

    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean maidspell$skipInsideStructure(WorldGenLevel level, BlockPos pos, BlockState state, int flags,
                                                  Operation<Boolean> original) {
        return !TerrainFeatureGuard.blocked(level, pos) && original.call(level, pos, state, flags);
    }
}
