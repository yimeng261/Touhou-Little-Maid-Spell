package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 通用 Jigsaw 结构，支持通过 JSON 字段 {@code avoid_water} 控制是否跳过水面生成。
 * <p>
 * Codec 字段与原版 {@code minecraft:jigsaw} 保持兼容（use_expansion_hack、
 * project_start_to_heightmap、liquid_settings 均可省略）。
 */
public class LandJigsawStructure extends Structure {

    public static final MapCodec<LandJigsawStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
                    Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.maxDepth),
                    HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
                    Codec.BOOL.optionalFieldOf("use_expansion_hack", false).forGetter(s -> s.useExpansionHack),
                    Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
                    Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.APPLY_WATERLOGGING).forGetter(s -> s.liquidSettings),
                    Codec.BOOL.optionalFieldOf("avoid_water", false).forGetter(s -> s.avoidWater)
            ).apply(instance, LandJigsawStructure::new)
    );

    private final Holder<StructureTemplatePool> startPool;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final LiquidSettings liquidSettings;
    private final boolean avoidWater;

    public LandJigsawStructure(
            StructureSettings settings,
            Holder<StructureTemplatePool> startPool,
            int maxDepth,
            HeightProvider startHeight,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            LiquidSettings liquidSettings,
            boolean avoidWater
    ) {
        super(settings);
        this.startPool = startPool;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.liquidSettings = liquidSettings;
        this.avoidWater = avoidWater;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;

        if (avoidWater) {
            int surfaceY = context.chunkGenerator().getFirstFreeHeight(
                    centerX, centerZ,
                    Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState()
            );
            if (surfaceY < context.chunkGenerator().getSeaLevel()) {
                return Optional.empty();
            }
        }

        int startY = this.startHeight.sample(
                context.random(),
                new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor())
        );
        BlockPos blockPos = new BlockPos(chunkPos.getMinBlockX(), startY, chunkPos.getMinBlockZ());

        return JigsawPlacement.addPieces(
                context, this.startPool, Optional.empty(), this.maxDepth,
                blockPos, this.useExpansionHack, this.projectStartToHeightmap,
                this.maxDistanceFromCenter, PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO, this.liquidSettings
        );
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.LAND_JIGSAW.get();
    }
}
