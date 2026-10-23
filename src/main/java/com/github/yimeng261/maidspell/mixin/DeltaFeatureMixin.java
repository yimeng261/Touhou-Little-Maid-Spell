package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.TerrainFeatureGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.DeltaFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 三角洲不把本模组标记的结构占地换成熔岩和岩浆块，见 {@link TerrainFeatureGuard}。
 */
@Mixin(DeltaFeature.class)
public abstract class DeltaFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void maidspell$findProtectedStructures(FeaturePlaceContext<DeltaFeatureConfiguration> context,
                                                   CallbackInfoReturnable<Boolean> cir) {
        TerrainFeatureGuard.begin(context.level(), context.origin());
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void maidspell$forgetProtectedStructures(FeaturePlaceContext<DeltaFeatureConfiguration> context,
                                                     CallbackInfoReturnable<Boolean> cir) {
        TerrainFeatureGuard.end();
    }

    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/feature/DeltaFeature;setBlock(Lnet/minecraft/world/level/LevelWriter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void maidspell$skipInsideStructure(DeltaFeature feature, LevelWriter level, BlockPos pos, BlockState state,
                                               Operation<Void> original) {
        if (!TerrainFeatureGuard.blocked(pos)) {
            original.call(feature, level, pos, state);
        }
    }
}
