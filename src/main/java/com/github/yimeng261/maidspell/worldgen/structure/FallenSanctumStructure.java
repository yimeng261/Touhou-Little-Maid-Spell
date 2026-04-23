package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 堕天圣堂结构。
 * 专门按下界绯红森林的地表高度寻找起点，不做平坦度检查。
 */
public class FallenSanctumStructure extends Structure {
    public static final Codec<FallenSanctumStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size),
                    Codec.intRange(1, 256)
                            .optionalFieldOf("max_distance_from_center", 256)
                            .forGetter(structure -> structure.maxDistanceFromCenter)
            ).apply(instance, FallenSanctumStructure::new)
    );

    private static final int MIN_AIR_CLEARANCE = 12;
    private static final int ROOF_MARGIN = 16;
    private static final int MIN_SEARCH_OFFSET = 8;
    private static final int MAX_SEARCH_ABOVE_SEA_LEVEL = 80;

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;

    public FallenSanctumStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
                                  int maxDistanceFromCenter) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        int x = context.chunkPos().getMinBlockX() + 8;
        int z = context.chunkPos().getMinBlockZ() + 8;
        OptionalInt surfaceY = findNetherSurfaceY(context, x, z);
        if (surfaceY.isEmpty()) {
            return Optional.empty();
        }

        BlockPos startPos = new BlockPos(x, surfaceY.getAsInt() + 1, z);
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
        return MaidSpellStructures.FALLEN_SANCTUM.get();
    }

    private static OptionalInt findNetherSurfaceY(GenerationContext context, int x, int z) {
        int minY = context.heightAccessor().getMinBuildHeight() + MIN_SEARCH_OFFSET;
        int maxY = Math.min(
                context.heightAccessor().getMaxBuildHeight() - ROOF_MARGIN,
                context.chunkGenerator().getSeaLevel() + MAX_SEARCH_ABOVE_SEA_LEVEL
        );
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());

        int clearAbove = 0;
        for (int y = maxY; y >= minY; y--) {
            BlockState state = column.getBlock(y);
            if (isOpenSpace(state)) {
                clearAbove++;
                continue;
            }

            if (isValidSurfaceBlock(state) && clearAbove >= MIN_AIR_CLEARANCE) {
                return OptionalInt.of(y);
            }

            clearAbove = 0;
        }

        return OptionalInt.empty();
    }

    private static boolean isOpenSpace(BlockState state) {
        return !state.blocksMotion() && state.getFluidState().isEmpty();
    }

    private static boolean isValidSurfaceBlock(BlockState state) {
        return state.is(Blocks.CRIMSON_NYLIUM)
                || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.MAGMA_BLOCK);
    }
}
