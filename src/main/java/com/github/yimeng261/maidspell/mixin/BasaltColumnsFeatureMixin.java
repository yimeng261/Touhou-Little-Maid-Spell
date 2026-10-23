package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.TerrainFeatureGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BasaltColumnsFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玄武岩三角洲的玄武岩石柱林不长进本模组标记的结构占地，见 {@link TerrainFeatureGuard}。
 */
@Mixin(BasaltColumnsFeature.class)
public abstract class BasaltColumnsFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void maidspell$findProtectedStructures(FeaturePlaceContext<ColumnFeatureConfiguration> context,
                                                   CallbackInfoReturnable<Boolean> cir) {
        TerrainFeatureGuard.begin(context.level(), context.origin());
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void maidspell$forgetProtectedStructures(FeaturePlaceContext<ColumnFeatureConfiguration> context,
                                                     CallbackInfoReturnable<Boolean> cir) {
        TerrainFeatureGuard.end();
    }

    @WrapOperation(method = "placeColumn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/feature/BasaltColumnsFeature;setBlock(Lnet/minecraft/world/level/LevelWriter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void maidspell$skipInsideStructure(BasaltColumnsFeature feature, LevelWriter level, BlockPos pos, BlockState state,
                                               Operation<Void> original) {
        if (!TerrainFeatureGuard.blocked(pos)) {
            original.call(feature, level, pos, state);
        }
    }
}
