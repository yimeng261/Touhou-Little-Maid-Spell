package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.neoforged.neoforge.common.conditions.ConditionContext;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.TrueCondition;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 条件化结构
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-11-08 14:38
 */
public class ConditionalJigsawStructure extends Structure {
    public static final DimensionPadding DEFAULT_DIMENSION_PADDING = DimensionPadding.ZERO;
    public static final LiquidSettings DEFAULT_LIQUID_SETTINGS = LiquidSettings.APPLY_WATERLOGGING;
    public static final int MAX_TOTAL_STRUCTURE_RANGE = 128;
    public static final int MIN_DEPTH = 0;
    public static final int MAX_DEPTH = 20;
    public static final MapCodec<ConditionalJigsawStructure> CODEC = RecordCodecBuilder.<ConditionalJigsawStructure>mapCodec(
                    instance -> instance.group(
                                    ICondition.LIST_CODEC.optionalFieldOf("generate_conditions", Collections.singletonList(TrueCondition.INSTANCE)).forGetter(structure -> structure.generateConditions),
                                    settingsCodec(instance),
                                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
                                    ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
                                    Codec.intRange(MIN_DEPTH, MAX_DEPTH).fieldOf("size").forGetter(s -> s.maxDepth),
                                    HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
                                    Codec.BOOL.fieldOf("use_expansion_hack").forGetter(s -> s.useExpansionHack),
                                    Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
                                    Codec.intRange(1, MAX_TOTAL_STRUCTURE_RANGE).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
                                    Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(s -> s.poolAliases),
                                    DimensionPadding.CODEC
                                            .optionalFieldOf("dimension_padding", DEFAULT_DIMENSION_PADDING)
                                            .forGetter(s -> s.dimensionPadding),
                                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", DEFAULT_LIQUID_SETTINGS).forGetter(s -> s.liquidSettings)
                            )
                            .apply(instance, ConditionalJigsawStructure::new)
            )
            .validate(ConditionalJigsawStructure::verifyRange);

    private final List<ICondition> generateConditions;
    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final List<PoolAliasBinding> poolAliases;
    private final DimensionPadding dimensionPadding;
    private final LiquidSettings liquidSettings;

    private static DataResult<ConditionalJigsawStructure> verifyRange(ConditionalJigsawStructure structure) {
        int i = switch (structure.terrainAdaptation()) {
            case NONE -> 0;
            case BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE -> 12;
        };
        return structure.maxDistanceFromCenter + i > MAX_TOTAL_STRUCTURE_RANGE
                ? DataResult.error(() -> "Structure size including terrain adaptation must not exceed 128")
                : DataResult.success(structure);
    }

    public ConditionalJigsawStructure(
            List<ICondition> generateConditions,
            Structure.StructureSettings settings,
            Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName,
            int maxDepth,
            HeightProvider startHeight,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            List<PoolAliasBinding> poolAliases,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings
    ) {
        super(settings);
        this.generateConditions = generateConditions;
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.poolAliases = poolAliases;
        this.dimensionPadding = dimensionPadding;
        this.liquidSettings = liquidSettings;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        TagManager tagManager = new TagManager(context.registryAccess());
        ConditionContext conditionContext = new ConditionContext(tagManager);
        if (!this.generateConditions.stream().allMatch(c -> c.test(conditionContext))) {
            return Optional.empty();
        }
        ChunkPos chunkpos = context.chunkPos();
        int i = this.startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        BlockPos blockpos = new BlockPos(chunkpos.getMinBlockX(), i, chunkpos.getMinBlockZ());
        return JigsawPlacement.addPieces(
                context,
                this.startPool,
                this.startJigsawName,
                this.maxDepth,
                blockpos,
                this.useExpansionHack,
                this.projectStartToHeightmap,
                this.maxDistanceFromCenter,
                PoolAliasLookup.create(this.poolAliases, blockpos, context.seed()),
                this.dimensionPadding,
                this.liquidSettings
        );
    }

    @Override
    public StructureType<?> type() {
        return MaidSpellStructures.CONDITIONAL_JIGSAW.get();
    }
}
