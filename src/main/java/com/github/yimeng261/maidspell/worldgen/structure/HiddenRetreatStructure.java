package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.common.WindSeekingBell.WindSeekingBell;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 隐世之境结构
 * 新特性：
 * 1. 只在由玩家使用寻风之铃创建的the_retreat维度生成
 *    维度命名格式：touhou_little_maid_spell:the_retreat_<player_uuid>
 * 2. 每个玩家的归隐之地维度只生成一个隐世之境
 * 3. 使用世界种子确定唯一生成位置（不限制距离）
 * 4. 保持原有的樱花林和地形平坦度要求
 */
public class HiddenRetreatStructure extends Structure {
    public static final MapCodec<HiddenRetreatStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size)
            ).apply(instance, HiddenRetreatStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private int height;

    // 地形平坦度检测的最大方差阈值（单位：方块高度的平方）
    private static final double MAX_TERRAIN_VARIANCE = 16.0; // 相当于标准差约4个方块
    // 每个区块的采样点数量
    private static final int SAMPLES_PER_CHUNK = 5;

    // 用于标记已生成过结构的维度（使用种子作为标识）
    public static final Set<Long> GENERATED_DIMENSIONS = ConcurrentHashMap.newKeySet();

    // 用于查找种子对应的维度
    public static final Map<Long, ServerLevel> DIMENSIONS_MAP = new ConcurrentHashMap<>();

    public HiddenRetreatStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
    }

    @Override
    public void afterPlace(WorldGenLevel pLevel, StructureManager pStructureManager, ChunkGenerator pChunkGenerator, RandomSource pRandom, BoundingBox pBoundingBox, ChunkPos pChunkPos, PiecesContainer pPieces) {
        Global.LOGGER.debug("[MaidSpell] afterPlace");
    }

    @Override
    public @NotNull StructureStart generate(RegistryAccess pRegistryAccess, ChunkGenerator pChunkGenerator, BiomeSource pBiomeSource, RandomState pRandomState, StructureTemplateManager pStructureTemplateManager, long pSeed, ChunkPos pChunkPos, int pReferences, LevelHeightAccessor pHeightAccessor, Predicate<Holder<Biome>> pValidBiome) {
        if (DIMENSIONS_MAP.containsKey(pSeed)) {
            return super.generate(pRegistryAccess, pChunkGenerator, pBiomeSource, pRandomState, pStructureTemplateManager, pSeed, pChunkPos, pReferences, pHeightAccessor, pValidBiome);
        }
        return StructureStart.INVALID_START;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        // 维度检查已在 ChunkGeneratorMixin 中完成，此处无需重复检查
        // Mixin 确保了只有在归隐之地维度才会调用此方法

        long worldSeed = context.seed();
        ChunkPos chunkPos = context.chunkPos();

        // 检查在mixin中进行
        Global.LOGGER.debug("[findGenerationPoint] 维度种子 {} 未标记，开始检查区块 {} 的地形", worldSeed, chunkPos);

        // 检查以当前区块为中心的5*5区块地形足够平坦
        if (!isValidGenerationLocation(context, chunkPos)) {
            Global.LOGGER.debug("[findGenerationPoint] 区块 {} 地形检查未通过，放弃生成", chunkPos);
            return Optional.empty();
        }

        BlockPos centerPos = new BlockPos(chunkPos.getMinBlockX() + 8, this.height, chunkPos.getMinBlockZ() + 8);
        Global.LOGGER.debug("[findGenerationPoint] 区块 {} 地形检查通过，尝试在 {} 生成结构", chunkPos, centerPos);

        Optional<GenerationStub> result = JigsawPlacement.addPieces(
                context,
                this.startPool,
                Optional.empty(), // 无可选目标池
                this.size,
                centerPos,
                false, // 不使用扩展高度
                Optional.empty(), // 无投影
                150, // 最大距离 - 增加以支持大型结构和垂直连接
                PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO,
                LiquidSettings.IGNORE_WATERLOGGING // 忽略含水方块处理
        );

        if (result.isPresent()) {
            ServerLevel serverLevel = DIMENSIONS_MAP.get(worldSeed);
            if (serverLevel != null) {
                WindSeekingBell.searchEngine.cacheManager.updateCache(serverLevel, centerPos);

                // 提交高优先级验证任务，确认结构真正生成完成
                WindSeekingBell.searchEngine.submitGenerationVerification(serverLevel, centerPos);
                MaidSpellMod.LOGGER.debug("隐世之境结构计划创建成功，位置: {}（已提交生成验证任务）", centerPos);
            } else {
                MaidSpellMod.LOGGER.warn("无法更新结构缓存：ServerLevel 为空 (种子: {})", worldSeed);
            }
        }

        return result;
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.HIDDEN_RETREAT.get();
    }

    /**
     * 检查以给定区块为中心的5*5区块地形足够平坦
     * @param context 生成上下文
     * @param centerChunk 中心区块位置
     * @return 如果区块地形平坦则返回true，否则返回false
     */
    private boolean isValidGenerationLocation(GenerationContext context, ChunkPos centerChunk) {
        //Global.LOGGER.debug("检查以给定区块为中心的5*5区块地形足够平坦，中心区块位置: {}", centerChunk);
        List<Integer> heightSamples = new ArrayList<>();
        int range = 2;
        int totalSamples = 0;
        int waterSamples = 0;

        // 检查以centerChunk为中心的5*5区块范围
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                SamplingResult result = sampleChunkHeights(context, checkChunk);
                heightSamples.addAll(result.heights);
                totalSamples += result.totalSamples;
                waterSamples += result.waterSamples;
            }
        }

        // 1. 检查水上点的比例，如果超过一半的点在水上则放弃生成
        if (totalSamples > 0 && waterSamples > totalSamples / 2) {
            return false;
        }

        // 2. 检查地形平坦度
        return isTerrainFlat(heightSamples);
    }

    /**
     * 采样结果，包含高度列表和水上点的数量
     */
    private record SamplingResult(List<Integer> heights, int totalSamples, int waterSamples) {}

    /**
     * 在指定区块内随机采样高度
     * @param context 生成上下文
     * @param chunk 目标区块
     * @return 采样结果，包含高度列表、总采样数和水上点数量
     */
    private SamplingResult sampleChunkHeights(GenerationContext context, ChunkPos chunk) {
        List<Integer> heights = new ArrayList<>();
        int waterCount = 0;

        // 创建基于区块坐标的确定性随机源，确保结果一致
        RandomSource random = RandomSource.create(chunk.toLong());

        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();

        for (int i = 0; i < SAMPLES_PER_CHUNK; i++) {
            // 在区块内随机选择一个位置
            int x = minX + random.nextInt(16);
            int z = minZ + random.nextInt(16);

            // 获取该位置的地面高度（不包括树叶）
            int surfaceHeight = context.chunkGenerator().getFirstOccupiedHeight(
                    x, z,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    context.heightAccessor(),
                    context.randomState()
            );

            // 获取海洋底部高度，用于判断是否有水
            int oceanFloorHeight = context.chunkGenerator().getFirstOccupiedHeight(
                    x, z,
                    Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(),
                    context.randomState()
            );

            // 如果海洋底部高度低于表面高度，说明这个位置有水
            if (oceanFloorHeight < surfaceHeight) {
                waterCount++;
            }

            heights.add(surfaceHeight);
        }

        return new SamplingResult(heights, SAMPLES_PER_CHUNK, waterCount);
    }

    /**
     * 检查地形是否足够平坦
     * @param heights 所有采样点的高度
     * @return 如果地形平坦度符合要求则返回true
     */
    private boolean isTerrainFlat(List<Integer> heights) {
        if (heights.size() < 2) {
            return true; // 样本过少，认为是平坦的
        }

        // 计算平均高度
        double sum = 0;
        for (int height : heights) {
            sum += height;
        }
        double mean = sum / heights.size();
        this.height = (int) mean;

        // 计算方差
        double varianceSum = 0;
        for (int height : heights) {
            double diff = height - mean;
            varianceSum += diff * diff;
        }
        double variance = varianceSum / heights.size();

        // 检查方差是否在可接受范围内
        return variance <= MAX_TERRAIN_VARIANCE;
    }

}