package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.CarverGuard;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 每个区块挖洞前后找出、清掉不许雕刻器挖的结构范围，见 {@link CarverGuard}。
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorCarverMixin {

    @Inject(method = "applyCarvers", at = @At("HEAD"))
    private void maidspell$findProtectedStructures(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                                                   StructureManager structureManager, ChunkAccess chunk,
                                                   GenerationStep.Carving step, CallbackInfo ci) {
        CarverGuard.begin(structureManager, level.registryAccess(), chunk.getPos());
    }

    @Inject(method = "applyCarvers", at = @At("RETURN"))
    private void maidspell$forgetProtectedStructures(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                                                     StructureManager structureManager, ChunkAccess chunk,
                                                     GenerationStep.Carving step, CallbackInfo ci) {
        CarverGuard.end();
    }
}
