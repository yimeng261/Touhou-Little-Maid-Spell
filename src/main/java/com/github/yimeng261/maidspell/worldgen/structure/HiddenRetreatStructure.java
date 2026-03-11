package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
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

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 隐世之境结构
 * 只在归隐之地维度生成，每个维度最多一个。
 */
public class HiddenRetreatStructure extends Structure {
    public static final MapCodec<HiddenRetreatStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size)
            ).apply(instance, HiddenRetreatStructure::new)
    );

    private final Holder<StructureTemplatePool> startPool;
    private final int size;

    // 地形平坦度检测参数
    private static final double MAX_TERRAIN_VARIANCE = 16.0;
    private static final int SAMPLES_PER_CHUNK = 5;
    private static final int TERRAIN_CHECK_RANGE = 2;

    /**
     * 线程局部标志：标记当前线程是否正在执行 generate() → super.generate() 调用链。
     * <p>
     * 当 generate() 调用 super.generate() 时设为 true，使得 findGenerationPoint() 知道
     * 是从 generate() 内部调用的，跳过搜索状态检查。
     * 当 canCreateStructure() 调用 findGenerationPoint() 时，此标志为 false，
     * 会检查搜索状态，防止 StructureCheck.featureChecks 缓存不该缓存的结果。
     */
    private static final ThreadLocal<Boolean> insideGenerate = ThreadLocal.withInitial(() -> false);

    public HiddenRetreatStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
    }

    // 去重机制：记录已处理的结构（按维度+中心位置去重）
    private static final Set<String> processedStructures = ConcurrentHashMap.newKeySet();

    /**
     * 清理结构去重记录。空字符串表示清理全部。
     */
    public static void cleanupProcessedStructures(String dimensionPrefix) {
        if (dimensionPrefix.isEmpty()) {
            int count = processedStructures.size();
            processedStructures.clear();
            MaidSpellMod.LOGGER.debug("清理全部结构去重记录，共 {} 条", count);
        } else {
            processedStructures.removeIf(key -> key.startsWith(dimensionPrefix));
            MaidSpellMod.LOGGER.debug("清理结构去重记录：{}", dimensionPrefix);
        }
    }

    @Override
    public void afterPlace(WorldGenLevel pLevel, StructureManager pStructureManager, ChunkGenerator pChunkGenerator,
                           RandomSource pRandom, BoundingBox pBoundingBox, ChunkPos pChunkPos, PiecesContainer pPieces) {
        ServerLevel serverLevel;
        if (pLevel instanceof ServerLevel sl) {
            serverLevel = sl;
        } else {
            try {
                serverLevel = pLevel.getLevel();
            } catch (Exception e) {
                MaidSpellMod.LOGGER.error("afterPlace: 无法获取 ServerLevel", e);
                return;
            }
        }

        ResourceKey<Level> dimKey = serverLevel.dimension();

        // 去重：afterPlace 对每个拼图块调用一次，只需处理一次
        BoundingBox structureBounds = pPieces.calculateBoundingBox();
        BlockPos structureCenter = new BlockPos(
                (structureBounds.minX() + structureBounds.maxX()) / 2,
                structureBounds.minY(),
                (structureBounds.minZ() + structureBounds.maxZ()) / 2
        );

        String structureKey = dimKey.location() + "@" + structureCenter.getX() + "," + structureCenter.getZ();
        if (!processedStructures.add(structureKey)) {
            return;
        }

        // 共享模式：addReference 标记结构为"已定位"，防止重复搜索到
        if (!Config.enablePrivateDimensions) {
            // 通过当前装饰区块的 STRUCTURE_REFERENCES 回溯到 StructureStart 所在区块
            // pChunkPos 是 placeInChunk 传入的当前装饰区块，其引用链一定在 WorldGenRegion 内
            for (StructureStart start : pStructureManager.startsForStructure(
                    SectionPos.bottomOf(pLevel.getChunk(pChunkPos.x, pChunkPos.z)), this)) {
                if (start.isValid() && start.canBeReferenced()) {
                    pStructureManager.addReference(start);
                    break;
                }
            }
        }

        // 取消强制加载，允许区块自然卸载
        RetreatManager.unforceLoadStructureChunks(dimKey, structureCenter);
    }

    @Override
    public @NotNull StructureStart generate(RegistryAccess pRegistryAccess, ChunkGenerator pChunkGenerator,
                                            BiomeSource pBiomeSource, RandomState pRandomState,
                                            StructureTemplateManager pStructureTemplateManager, long pSeed,
                                            ChunkPos pChunkPos, int pReferences, LevelHeightAccessor pHeightAccessor,
                                            Predicate<Holder<Biome>> pValidBiome) {
        // 通过 ChunkGenerator 获取当前维度，确认是归隐之地维度
        if (pChunkGenerator instanceof com.github.yimeng261.maidspell.worldgen.accessor.ChunkGeneratorAccessor accessor) {
            ResourceKey<Level> dimKey = accessor.maidspell$getDimensionKey();
            if (dimKey != null && dimKey.location().getNamespace().equals(MaidSpellMod.MOD_ID)
                    && dimKey.location().getPath().startsWith("the_retreat")) {
                // 私人模式：每个维度只允许生成一个结构
                if (Config.enablePrivateDimensions) {
                    if (!RetreatManager.tryMarkStructureGenerated(dimKey)) {
                        return StructureStart.INVALID_START;
                    }
                    MaidSpellMod.LOGGER.debug("首次在维度 {} 尝试生成结构，区块 {}", dimKey.location(), pChunkPos);

                    StructureStart result = callSuperGenerate(pRegistryAccess, pChunkGenerator, pBiomeSource, pRandomState,
                            pStructureTemplateManager, pSeed, pChunkPos, pReferences, pHeightAccessor, pValidBiome);

                    if (!result.isValid()) {
                        // 生成失败，回退标记
                        RetreatManager.unmarkStructureGenerated(dimKey);
                        MaidSpellMod.LOGGER.debug("结构生成失败，回退标记 - 维度: {}, 区块: {}", dimKey.location(), pChunkPos);
                    }

                    return result;
                }

                // 共享模式：CAS 获取搜索许可，保证并发安全
                if (Config.enableSharedQuotaLimit) {
                    if (!RetreatManager.tryAcquireSearchPermit()) {
                        // 没有搜索在进行，或许可已被其他并发 generate() 消费
                        return StructureStart.INVALID_START;
                    }
                    // 许可已获取，执行生成
                    StructureStart sharedResult = callSuperGenerate(pRegistryAccess, pChunkGenerator, pBiomeSource, pRandomState,
                            pStructureTemplateManager, pSeed, pChunkPos, pReferences, pHeightAccessor, pValidBiome);
                    if (sharedResult.isValid()) {
                        // 生成成功：许可已消费，存储位置让 SearchWorker 立即感知
                        BlockPos structurePos = new BlockPos(pChunkPos.getMinBlockX(), 0, pChunkPos.getMinBlockZ());
                        RetreatManager.setGeneratedStructurePos(dimKey, structurePos);
                        MaidSpellMod.LOGGER.debug("共享模式结构生成成功 - 维度: {}, 区块: {}", dimKey.location(), pChunkPos);
                    } else {
                        // 生成失败（地形不合适等）：归还许可，允许后续候选区块重试
                        RetreatManager.releaseSearchPermit();
                    }
                    return sharedResult;
                }
                // 共享模式但未启用配额限制：直接生成
                return callSuperGenerate(pRegistryAccess, pChunkGenerator, pBiomeSource, pRandomState,
                        pStructureTemplateManager, pSeed, pChunkPos, pReferences, pHeightAccessor, pValidBiome);
            }
        } else {
            MaidSpellMod.LOGGER.warn("ChunkGenerator 不是 ChunkGeneratorAccessor 实例 - 区块: {}, 类型: {}", pChunkPos, pChunkGenerator.getClass().getName());
        }
        return StructureStart.INVALID_START;
    }

    /**
     * 包装 super.generate()，设置 insideGenerate 线程局部标志。
     * 使得 findGenerationPoint() 知道是从 generate() 内部调用的。
     */
    private StructureStart callSuperGenerate(RegistryAccess pRegistryAccess, ChunkGenerator pChunkGenerator,
                                             BiomeSource pBiomeSource, RandomState pRandomState,
                                             StructureTemplateManager pStructureTemplateManager, long pSeed,
                                             ChunkPos pChunkPos, int pReferences, LevelHeightAccessor pHeightAccessor,
                                             Predicate<Holder<Biome>> pValidBiome) {
        insideGenerate.set(true);
        try {
            return super.generate(pRegistryAccess, pChunkGenerator, pBiomeSource, pRandomState,
                    pStructureTemplateManager, pSeed, pChunkPos, pReferences, pHeightAccessor, pValidBiome);
        } finally {
            insideGenerate.set(false);
        }
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        // 如果不是从 generate() 内部调用（即从 canCreateStructure 调用），
        // 检查搜索状态。在共享配额模式下，无搜索活动时返回 empty，
        // 避免 StructureCheck.featureChecks 缓存 true 导致幽灵结构。
        if (!insideGenerate.get() && !Config.enablePrivateDimensions && Config.enableSharedQuotaLimit) {
            if (!RetreatManager.isSearchActive()) {
                return Optional.empty();
            }
        }

        ChunkPos chunkPos = context.chunkPos();

        // 地形检查
        OptionalInt terrainHeight = checkTerrainAndGetHeight(context, chunkPos);
        if (terrainHeight.isEmpty()) {
            return Optional.empty();
        }

        int height = terrainHeight.getAsInt();
        BlockPos centerPos = new BlockPos(chunkPos.getMinBlockX() + 8, height, chunkPos.getMinBlockZ() + 8);

        return JigsawPlacement.addPieces(
                context, this.startPool, Optional.empty(), this.size,
                centerPos, false, Optional.empty(), 150,
                PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO,
                LiquidSettings.IGNORE_WATERLOGGING
        );
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.HIDDEN_RETREAT.get();
    }

    /**
     * 检查地形并返回平均高度。
     * <p>
     * 使用 NoiseRouter 的 depth 密度函数通过二分法估算地表高度，
     * 配合 Welford 在线算法增量计算方差，方差超阈值时立即退出。
     * 同时通过比较估算高度与海平面检测水域。
     */
    private OptionalInt checkTerrainAndGetHeight(GenerationContext context, ChunkPos centerChunk) {
        DensityFunction depthFn = context.randomState().router().depth();
        int seaLevel = context.chunkGenerator().getSeaLevel();

        int n = 0;
        double mean = 0.0;
        double m2 = 0.0; // Welford's M2 累积量
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

                    // Welford's online algorithm
                    double delta = surfaceY - mean;
                    mean += delta / n;
                    double delta2 = surfaceY - mean;
                    m2 += delta * delta2;

                    // 早退出：方差已超阈值
                    if (n >= SAMPLES_PER_CHUNK * 2 && m2 / n > MAX_TERRAIN_VARIANCE) {
                        return OptionalInt.empty();
                    }

                    // 水域检测：地表低于海平面即视为水体
                    if (surfaceY < seaLevel) {
                        waterSamples++;
                        if (waterSamples > n / 10) {
                            return OptionalInt.empty();
                        }
                    }
                }
            }
        }

        // 最终方差检查
        if (n < 2) {
            return OptionalInt.empty();
        }
        if (m2 / n > MAX_TERRAIN_VARIANCE) {
            return OptionalInt.empty();
        }

        return OptionalInt.of((int) mean);
    }

    /**
     * 使用 depth 密度函数二分法估算地表 Y 坐标。
     * <p>
     * depth 在地表处 ≈ 0，地上 &lt; 0，地下 &gt; 0。
     * 通过二分法在 [minY, maxY] 范围内找到 depth ≈ 0 的 Y 值。
     * 精度为 ±1 格（二分法收敛到范围宽度 ≤ 2）。
     */
    private static int estimateSurfaceHeight(DensityFunction depthFn, int x, int z, GenerationContext context) {
        int minY = context.heightAccessor().getMinBuildHeight();
        int maxY = context.heightAccessor().getMaxBuildHeight();

        // 二分法：找 depth 从正（地下）变为负（地上）的边界
        int lo = minY;
        int hi = maxY;

        while (hi - lo > 2) {
            int mid = (lo + hi) >>> 1;
            double depthAtMid = depthFn.compute(new DensityFunction.SinglePointContext(x, mid, z));
            if (depthAtMid > 0) {
                // 还在地下，向上搜索
                lo = mid;
            } else {
                // 已在地上或地表，向下搜索
                hi = mid;
            }
        }

        return hi;
    }
}
