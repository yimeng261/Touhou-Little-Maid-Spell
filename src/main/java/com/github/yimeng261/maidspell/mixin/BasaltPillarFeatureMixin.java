package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.TerrainFeatureGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BasaltPillarFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 灵魂沙峡谷的玄武岩柱不长进本模组标记的结构占地，见 {@link TerrainFeatureGuard}。
 */
@Mixin(BasaltPillarFeature.class)
public abstract class BasaltPillarFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void maidspell$findProtectedStructures(FeaturePlaceContext<NoneFeatureConfiguration> context,
                                                   CallbackInfoReturnable<Boolean> cir) {
        TerrainFeatureGuard.begin(context.level(), context.origin());
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void maidspell$forgetProtectedStructures(FeaturePlaceContext<NoneFeatureConfiguration> context,
                                                     CallbackInfoReturnable<Boolean> cir) {
        TerrainFeatureGuard.end();
    }

    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean maidspell$skipPillarInsideStructure(WorldGenLevel level, BlockPos pos, BlockState state, int flags,
                                                        Operation<Boolean> original) {
        return !TerrainFeatureGuard.blocked(pos) && original.call(level, pos, state, flags);
    }

    @WrapOperation(method = {"placeBaseHangOff", "placeHangOff"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean maidspell$skipHangOffInsideStructure(LevelAccessor level, BlockPos pos, BlockState state, int flags,
                                                         Operation<Boolean> original) {
        return !TerrainFeatureGuard.blocked(pos) && original.call(level, pos, state, flags);
    }
}
