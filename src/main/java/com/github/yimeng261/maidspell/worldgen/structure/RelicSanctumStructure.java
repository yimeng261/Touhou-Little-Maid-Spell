package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 堕天圣堂结构。
 * 使用标准 jigsaw 放置逻辑，在地表附近生成三段式结构。
 */
public class RelicSanctumStructure extends Structure {
    public static final Codec<RelicSanctumStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size),
                    Codec.intRange(1, 256)
                            .optionalFieldOf("max_distance_from_center", 256)
                            .forGetter(structure -> structure.maxDistanceFromCenter)
            ).apply(instance, RelicSanctumStructure::new)
    );

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;

    private static final double MAX_TERRAIN_VARIANCE = 16.0;
    private static final int SAMPLES_PER_CHUNK = 5;
    private static final int TERRAIN_CHECK_RANGE = 1; // center + 3x3 chunks

    public RelicSanctumStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
                                 int maxDistanceFromCenter) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        OptionalInt terrainHeight = checkTerrainAndGetHeight(context, chunkPos);
        if (terrainHeight.isEmpty()) {
            return Optional.empty();
        }

        DensityFunction depthFn = context.randomState().router().depth();
        int x = chunkPos.getMinBlockX() + 8;
        int z = chunkPos.getMinBlockZ() + 8;
        int surfaceY = estimateSurfaceHeight(depthFn, x, z, context);

        BlockPos startPos = new BlockPos(x, surfaceY + 1, z);
        return JigsawPlacement.addPieces(
                context,
                this.startPool,
                Optional.empty(),
                this.size,
                startPos,
                false,
                Optional.empty(),
                this.maxDistanceFromCenter
        );
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.RELIC_SANCTUM.get();
    }

    private OptionalInt checkTerrainAndGetHeight(GenerationContext context, ChunkPos centerChunk) {
        DensityFunction depthFn = context.randomState().router().depth();
        DensityFunction finalDensityFn = context.randomState().router().finalDensity();
        int seaLevel = context.chunkGenerator().getSeaLevel();

        int n = 0;
        double mean = 0.0;
        double m2 = 0.0;
        int waterSamples = 0;

        for (int dx = -TERRAIN_CHECK_RANGE; dx <= TERRAIN_CHECK_RANGE; dx++) {
            for (int dz = -TERRAIN_CHECK_RANGE; dz <= TERRAIN_CHECK_RANGE; dz++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                RandomSource random = RandomSource.create(checkChunk.toLong());
                int minX = checkChunk.getMinBlockX();
                int minZ = checkChunk.getMinBlockZ();

                for (int i = 0; i < SAMPLES_PER_CHUNK; i++) {
                    int x = minX + random.nextInt(16);
                    int z = minZ + random.nextInt(16);
                    int surfaceY = estimateSurfaceHeight(depthFn, x, z, context);
                    n++;

                    double delta = surfaceY - mean;
                    mean += delta / n;
                    double delta2 = surfaceY - mean;
                    m2 += delta * delta2;

                    if (n >= SAMPLES_PER_CHUNK * 2 && m2 / n > MAX_TERRAIN_VARIANCE) {
                        return OptionalInt.empty();
                    }

                    if (surfaceY < seaLevel) {
                        waterSamples++;
                    } else if (surfaceY < seaLevel + 5) {
                        if (finalDensityFn.compute(new DensityFunction.SinglePointContext(x, seaLevel - 1, z)) <= 0) {
                            waterSamples++;
                        }
                    }

                    if (waterSamples > n / 10) {
                        return OptionalInt.empty();
                    }
                }
            }
        }

        if (n < 2 || m2 / n > MAX_TERRAIN_VARIANCE) {
            return OptionalInt.empty();
        }

        int meanHeight = (int) mean;
        if (meanHeight < seaLevel || waterSamples * 10 >= n) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(meanHeight);
    }

    private static int estimateSurfaceHeight(DensityFunction depthFn, int x, int z, GenerationContext context) {
        int minY = context.heightAccessor().getMinBuildHeight();
        int maxY = context.heightAccessor().getMaxBuildHeight();
        int lo = minY;
        int hi = maxY;

        while (hi - lo > 2) {
            int mid = (lo + hi) >>> 1;
            double depth = depthFn.compute(new DensityFunction.SinglePointContext(x, mid, z));
            if (depth > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        return lo;
    }
}
