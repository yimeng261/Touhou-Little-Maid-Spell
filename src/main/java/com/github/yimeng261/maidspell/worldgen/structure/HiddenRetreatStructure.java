package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.dimension.SharedRetreatManager;
import com.github.yimeng261.maidspell.dimension.StructureSearchQueue;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
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

    // 地形平坦度检测的最大方差阈值（标准差约8格，适应轻微起伏地形）
    private static final double MAX_TERRAIN_VARIANCE = 64.0;
    private static final int SAMPLES_PER_CHUNK = 5;

    public HiddenRetreatStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
    }

    // 去重机制：记录已处理的结构（按维度+中心位置去重）
    private static final Set<String> processedStructures = ConcurrentHashMap.newKeySet();

    /**
     * 清理结构去重记录
     *
     * @param dimensionPrefix 维度前缀，格式："namespace:dimension@"。空字符串表示清理全部。
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
        // 关键修复：WorldGenRegion 也有 getLevel() 方法可获取底层 ServerLevel
        ServerLevel serverLevel;
        if (pLevel instanceof ServerLevel sl) {
            serverLevel = sl;
        } else {
            // WorldGenRegion 等情况
            try {
                serverLevel = pLevel.getLevel();
            } catch (Exception e) {
                MaidSpellMod.LOGGER.error("afterPlace: 无法获取 ServerLevel", e);
                return;
            }
        }

        ResourceKey<Level> dimKey = serverLevel.dimension();

        // 去重：afterPlace 会对每个拼图块调用一次，我们只需要处理一次整个结构
        // 使用 PiecesContainer 的整体 BoundingBox 来计算唯一的结构中心
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

        // 标记该维度已生成结构
        if (Config.enablePrivateDimensions) {
            RetreatManager.markStructureGenerated(dimKey);
        }

        // 关键修复：在结构实际生成完成后才消耗配额（避免地形失败导致配额浪费）
        if (!Config.enablePrivateDimensions) {
            com.github.yimeng261.maidspell.dimension.SharedRetreatManager.consumeQuota(serverLevel.getServer());
            MaidSpellMod.LOGGER.info("结构实际生成完成，消耗配额 - 位置: {}", structureCenter);
        }

        // FIFO 通知队列中第一个等待的玩家
        long worldSeed = serverLevel.getSeed();
        UUID notifiedPlayer = StructureSearchQueue.notifyNextPlayer(worldSeed, structureCenter);

        if (notifiedPlayer != null) {
            MaidSpellMod.LOGGER.info("隐世之境结构生成完成，通知玩家: {}", notifiedPlayer);
            // 将结构位置写入该玩家的缓存（私人/共享模式均适用）
            RetreatManager.updateCache(notifiedPlayer, structureCenter);

            // 共享模式：队列中还有等待的玩家，触发下一次搜索
            if (!Config.enablePrivateDimensions && StructureSearchQueue.hasWaitingRequests(worldSeed)) {
                RetreatManager.triggerStructureSearch(serverLevel, serverLevel.getSharedSpawnPos());
            }

            serverLevel.getServer().execute(() -> {
                var player = serverLevel.getServer().getPlayerList().getPlayer(notifiedPlayer);
                if (player != null) {
                    int distance = (int) Math.sqrt(player.blockPosition().distSqr(structureCenter));
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "item.touhou_little_maid_spell.wind_seeking_bell.structure_generated",
                                    structureCenter.getX(), structureCenter.getY(), structureCenter.getZ(), distance
                            ).withStyle(net.minecraft.ChatFormatting.GREEN)
                    );
                }
            });
        } else {
            // 修复孤儿结构问题：队列为空时，将结构加入未分配池（仅共享模式）
            if (!Config.enablePrivateDimensions) {
                RetreatManager.addUnassignedStructure(worldSeed, structureCenter);
                MaidSpellMod.LOGGER.info("隐世之境自然生成，加入未分配池 - 位置: {}", structureCenter);
            }
        }

        // 关键：取消强制加载，允许区块自然卸载
        RetreatManager.unforceLoadStructureChunks(dimKey, structureCenter);
    }

    @Override
    public @NotNull StructureStart generate(RegistryAccess pRegistryAccess, ChunkGenerator pChunkGenerator,
                                            BiomeSource pBiomeSource, RandomState pRandomState,
                                            StructureTemplateManager pStructureTemplateManager, long pSeed,
                                            ChunkPos pChunkPos, int pReferences, LevelHeightAccessor pHeightAccessor,
                                            Predicate<Holder<Biome>> pValidBiome) {
        // 通过 seed 查找对应的维度 key，确认是归隐之地维度
        ResourceKey<Level> dimKey = RetreatManager.findDimensionKeyBySeed(pSeed);
        if (dimKey != null) {
            return super.generate(pRegistryAccess, pChunkGenerator, pBiomeSource, pRandomState,
                    pStructureTemplateManager, pSeed, pChunkPos, pReferences, pHeightAccessor, pValidBiome);
        }
        return StructureStart.INVALID_START;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        long worldSeed = context.seed();
        ChunkPos chunkPos = context.chunkPos();

        ResourceKey<Level> dimKey = RetreatManager.findDimensionKeyBySeed(worldSeed);
        ServerLevel serverLevel = dimKey != null ? RetreatManager.getDimensionLevel(dimKey) : null;

        // 共享模式下检查配额
        if (!Config.enablePrivateDimensions && serverLevel != null) {
            if (!SharedRetreatManager.hasAvailableQuota(serverLevel.getServer())) {
                return Optional.empty();
            }
        }

        // 地形检查（返回平均高度，避免竞态条件）
        OptionalInt terrainHeight = checkTerrainAndGetHeight(context, chunkPos);
        if (terrainHeight.isEmpty()) {
            return Optional.empty();
        }

        int height = terrainHeight.getAsInt();
        BlockPos centerPos = new BlockPos(chunkPos.getMinBlockX() + 8, height, chunkPos.getMinBlockZ() + 8);

        Optional<GenerationStub> result = JigsawPlacement.addPieces(
                context, this.startPool, Optional.empty(), this.size,
                centerPos, false, Optional.empty(), 150,
                PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO,
                LiquidSettings.IGNORE_WATERLOGGING
        );

        if (result.isPresent()) {
            MaidSpellMod.LOGGER.info("隐世之境结构计划生成，位置: {}", centerPos);
            // 注意：配额在 afterPlace 中消耗，确保结构真正生成后才消耗
        }

        return result;
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.HIDDEN_RETREAT.get();
    }

    /**
     * 检查地形并返回平均高度（线程安全，不写入实例字段）
     * @return 平均高度，如果地形不合适则返回 empty
     */
    private OptionalInt checkTerrainAndGetHeight(GenerationContext context, ChunkPos centerChunk) {
        List<Integer> heightSamples = new ArrayList<>();
        int range = 2;
        int totalSamples = 0;
        int waterSamples = 0;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                SamplingResult sr = sampleChunkHeights(context, checkChunk);
                heightSamples.addAll(sr.heights);
                totalSamples += sr.totalSamples;
                waterSamples += sr.waterSamples;
            }
        }

        // 水域比例检测
        if (totalSamples > 0 && waterSamples > totalSamples / 2) {
            return OptionalInt.empty();
        }

        // 平坦度检测
        return computeFlatHeight(heightSamples);
    }

    private record SamplingResult(List<Integer> heights, int totalSamples, int waterSamples) {}

    private SamplingResult sampleChunkHeights(GenerationContext context, ChunkPos chunk) {
        List<Integer> heights = new ArrayList<>();
        int waterCount = 0;
        RandomSource random = RandomSource.create(chunk.toLong());
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();

        for (int i = 0; i < SAMPLES_PER_CHUNK; i++) {
            int x = minX + random.nextInt(16);
            int z = minZ + random.nextInt(16);

            int surfaceHeight = context.chunkGenerator().getFirstOccupiedHeight(
                    x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    context.heightAccessor(), context.randomState()
            );
            int oceanFloorHeight = context.chunkGenerator().getFirstOccupiedHeight(
                    x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState()
            );

            if (oceanFloorHeight < surfaceHeight) {
                waterCount++;
            }
            heights.add(surfaceHeight);
        }

        return new SamplingResult(heights, SAMPLES_PER_CHUNK, waterCount);
    }

    /**
     * 计算平坦度并返回平均高度（纯函数，无副作用）
     */
    private OptionalInt computeFlatHeight(List<Integer> heights) {
        if (heights.size() < 2) {
            return heights.isEmpty() ? OptionalInt.empty() : OptionalInt.of(heights.get(0));
        }

        double sum = 0;
        for (int h : heights) sum += h;
        double mean = sum / heights.size();

        double varianceSum = 0;
        for (int h : heights) {
            double diff = h - mean;
            varianceSum += diff * diff;
        }
        double variance = varianceSum / heights.size();

        if (variance > MAX_TERRAIN_VARIANCE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) mean);
    }
}
