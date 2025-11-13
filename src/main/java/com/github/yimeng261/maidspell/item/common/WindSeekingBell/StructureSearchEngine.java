package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import com.github.yimeng261.maidspell.Global;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 结构搜索引擎
 * 负责执行并行的隐世之境结构搜索
 */
public class StructureSearchEngine {

    // 结构验证优化：提取静态常量避免重复创建
    private static final ResourceLocation HIDDEN_RETREAT_LOCATION =
        new ResourceLocation("touhou_little_maid_spell", "hidden_retreat");
    private static final ResourceKey<Structure> HIDDEN_RETREAT_KEY =
        ResourceKey.create(Registries.STRUCTURE, HIDDEN_RETREAT_LOCATION);

    // 结构集合缓存：延迟初始化以避免早期加载问题
    private static volatile HolderSet<Structure> cachedStructureSet = null;
    private static final Object STRUCTURE_SET_LOCK = new Object();

    // 结构检查分段锁
    private static final StripedLock STRUCTURE_CHECK_LOCK = new StripedLock(SearchConfig.LOCK_STRIPE_COUNT);

    // 线程池
    private static final ThreadPoolExecutor SEARCH_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(
        SearchConfig.getRecommendedThreadPoolSize(),
        r -> {
            Thread t = new Thread(r, SearchConfig.THREAD_NAME_PREFIX + System.currentTimeMillis());
            t.setDaemon(true);
            t.setPriority(SearchConfig.THREAD_PRIORITY);
            return t;
        }
    );

    private final SearchCacheManager cacheManager;

    public StructureSearchEngine(SearchCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 异步并行搜索隐世之境结构（简化版）
     * @param level 服务器世界
     * @param playerPos 玩家位置
     * @return 搜索结果的CompletableFuture
     */
    public CompletableFuture<BlockPos> searchAsync(ServerLevel level, BlockPos playerPos) {
        // 1. 首先检查缓存（按维度缓存）
        SearchCacheManager.CacheCheckResult cacheResult = cacheManager.checkCache(level);
        if (cacheResult.hasCache) {
            Global.LOGGER.debug("Structure found in cache for dimension: {}", level.dimension().location());
            return CompletableFuture.completedFuture(cacheResult.structurePos);
        }

        // 2. 检查是否已有相同维度的搜索在进行（使用维度级别的键）
        String searchKey = cacheManager.generateDimensionKey(level);
        CompletableFuture<BlockPos> existingSearch = cacheManager.getOngoingSearch(searchKey);
        if (existingSearch != null) {
            Global.LOGGER.debug("Reusing ongoing search for dimension: {}", level.dimension().location());
            return existingSearch;
        }

        // 3. 启动新的异步搜索
        Global.LOGGER.info("Starting new structure search for dimension: {}", level.dimension().location());
        CompletableFuture<BlockPos> searchFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return searchParallel(level, playerPos);
            } catch (Exception e) {
                Global.LOGGER.error("Structure search failed for dimension: {}", level.dimension().location(), e);
                return null;
            }
        }, SEARCH_EXECUTOR).whenComplete((result, throwable) -> {
            cacheManager.removeSearch(searchKey);
            if (result != null) {
                Global.LOGGER.info("Structure search completed successfully for dimension: {}, found at: {}",
                    level.dimension().location(), result);
            } else {
                Global.LOGGER.warn("Structure search completed but no structure found in dimension: {}",
                    level.dimension().location());
            }
        });

        cacheManager.registerSearch(searchKey, searchFuture);
        return searchFuture;
    }

    /**
     * 并行搜索隐世之境结构
     */
    private BlockPos searchParallel(ServerLevel level, BlockPos playerPos) {
        // 1. 首先检查缓存
        SearchCacheManager.CacheCheckResult cacheResult = cacheManager.checkCache(level);
        if (cacheResult.hasCache) {
            return cacheResult.structurePos;
        }

        ChunkPos playerChunk = new ChunkPos(playerPos);

        // 2. 执行并行搜索
        BlockPos result = parallelSquareSearch(level, playerChunk);

        // 3. 将结果加入缓存
        cacheManager.updateCache(level, result);

        return result;
    }

    /**
     * 多线程并行小方格螺旋搜索
     */
    private BlockPos parallelSquareSearch(ServerLevel level, ChunkPos centerChunk) {
        int availableThreads = SearchConfig.getRecommendedThreadPoolSize();

        Global.LOGGER.debug("Starting parallel search with {} threads, radius: {}",
                           availableThreads, SearchConfig.MAX_SEARCH_RADIUS);

        int maxSectorLayer = (SearchConfig.MAX_SEARCH_RADIUS + SearchConfig.SECTOR_SIZE - 1) / SearchConfig.SECTOR_SIZE;

        for (int sectorLayer = 0; sectorLayer <= maxSectorLayer; sectorLayer++) {
            BlockPos result = searchSectorLayerParallel(level, centerChunk, sectorLayer);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * 多线程并行搜索指定小方格层
     */
    private BlockPos searchSectorLayerParallel(ServerLevel level, ChunkPos centerChunk, int sectorLayer) {
        if (sectorLayer == 0) {
            return searchSectorComplete(level, centerChunk, 0, 0);
        }

        List<int[]> sectorCoords = generateSectorCoordinates(sectorLayer);
        List<CompletableFuture<BlockPos>> sectorTasks = new ArrayList<>();
        AtomicReference<BlockPos> foundInLayer = new AtomicReference<>(null);
        AtomicBoolean layerComplete = new AtomicBoolean(false);

        for (final int[] coords : sectorCoords) {
            final int sectorX = coords[0];
            final int sectorZ = coords[1];

            CompletableFuture<BlockPos> sectorTask = CompletableFuture.supplyAsync(() ->
                            searchSectorWithTermination(level, centerChunk, sectorX, sectorZ, layerComplete),
                    SEARCH_EXECUTOR);

            sectorTasks.add(sectorTask);
        }

        try {
            CompletableFuture<BlockPos> monitorTask = CompletableFuture.supplyAsync(() -> {
                try {
                    while (foundInLayer.get() == null && !areAllTasksCompleted(sectorTasks)) {
                        Thread.sleep(50);

                        for (CompletableFuture<BlockPos> task : sectorTasks) {
                            if (task.isDone() && !task.isCancelled()) {
                                try {
                                    BlockPos result = task.get();
                                    if (result != null && foundInLayer.compareAndSet(null, result)) {
                                        layerComplete.set(true);
                                        return result;
                                    }
                                } catch (Exception e) {
                                    // 忽略单个任务的异常
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return foundInLayer.get();
            });

            BlockPos result = monitorTask.get(120, TimeUnit.SECONDS);

            if (result != null) {
                sectorTasks.forEach(task -> task.cancel(true));
            } else {
                CompletableFuture.allOf(sectorTasks.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            }

            return result;

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            sectorTasks.forEach(task -> task.cancel(true));
            return null;
        }
    }

    private boolean areAllTasksCompleted(List<CompletableFuture<BlockPos>> tasks) {
        return tasks.stream().allMatch(CompletableFuture::isDone);
    }

    private List<int[]> generateSectorCoordinates(int layer) {
        List<int[]> coords = new ArrayList<>();

        if (layer == 0) {
            coords.add(new int[]{0, 0});
            return coords;
        }

        for (int x = -layer; x <= layer; x++) {
            coords.add(new int[]{x, layer});
        }

        for (int z = layer - 1; z >= -layer; z--) {
            coords.add(new int[]{layer, z});
        }

        for (int x = layer - 1; x >= -layer; x--) {
            coords.add(new int[]{x, -layer});
        }

        for (int z = -layer + 1; z <= layer - 1; z++) {
            coords.add(new int[]{-layer, z});
        }

        return coords;
    }

    private BlockPos searchSectorWithTermination(ServerLevel level, ChunkPos centerChunk, int sectorX, int sectorZ,
                                                 AtomicBoolean layerComplete) {
        if (layerComplete.get()) {
            return null;
        }

        return searchSectorComplete(level, centerChunk, sectorX, sectorZ);
    }

    private BlockPos searchSectorComplete(ServerLevel level, ChunkPos centerChunk, int sectorX, int sectorZ) {
        int sectorStartX = centerChunk.x + sectorX * SearchConfig.SECTOR_SIZE - SearchConfig.SECTOR_SIZE / 2;
        int sectorEndX = sectorStartX + SearchConfig.SECTOR_SIZE - 1;
        int sectorStartZ = centerChunk.z + sectorZ * SearchConfig.SECTOR_SIZE - SearchConfig.SECTOR_SIZE / 2;
        int sectorEndZ = sectorStartZ + SearchConfig.SECTOR_SIZE - 1;

        sectorStartX = Math.max(sectorStartX, centerChunk.x - SearchConfig.MAX_SEARCH_RADIUS);
        sectorEndX = Math.min(sectorEndX, centerChunk.x + SearchConfig.MAX_SEARCH_RADIUS);
        sectorStartZ = Math.max(sectorStartZ, centerChunk.z - SearchConfig.MAX_SEARCH_RADIUS);
        sectorEndZ = Math.min(sectorEndZ, centerChunk.z + SearchConfig.MAX_SEARCH_RADIUS);

        int sectorWidth = sectorEndX - sectorStartX + 1;
        int sectorHeight = sectorEndZ - sectorStartZ + 1;
        BitSet sectorChecked = new BitSet(sectorWidth * sectorHeight);

        ChunkPos sectorCenter = new ChunkPos((sectorStartX + sectorEndX) / 2, (sectorStartZ + sectorEndZ) / 2);
        int sectorRadius = Math.max(sectorWidth, sectorHeight) / 2;

        for (int layer = 0; layer <= sectorRadius; layer += SearchConfig.SEARCH_STEP) {
            BlockPos result = searchSectorLayer(level, sectorCenter, layer, sectorStartX, sectorEndX,
                                              sectorStartZ, sectorEndZ, sectorChecked);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private BlockPos searchSectorLayer(ServerLevel level, ChunkPos sectorCenter, int layer,
                                     int minX, int maxX, int minZ, int maxZ,
                                     BitSet sectorChecked) {
        if (layer == 0) {
            if (isInSectorBounds(sectorCenter, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(sectorCenter, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(sectorCenter, minX, minZ, sectorChecked, maxX - minX + 1);
                    return checkPotentialCenter(level, sectorCenter);
                }
            }
            return null;
        }

        for (int x = -layer; x <= layer; x += SearchConfig.SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x + x, sectorCenter.z + layer);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }

        for (int z = layer - SearchConfig.SEARCH_STEP; z >= -layer; z -= SearchConfig.SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x + layer, sectorCenter.z + z);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }

        for (int x = layer - SearchConfig.SEARCH_STEP; x >= -layer; x -= SearchConfig.SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x + x, sectorCenter.z - layer);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }

        for (int z = -layer + SearchConfig.SEARCH_STEP; z <= layer - SearchConfig.SEARCH_STEP; z += SearchConfig.SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x - layer, sectorCenter.z + z);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }

        return null;
    }

    private boolean isInSectorBounds(ChunkPos pos, int minX, int maxX, int minZ, int maxZ) {
        return pos.x >= minX && pos.x <= maxX && pos.z >= minZ && pos.z <= maxZ;
    }

    private boolean isSectorChunkUnchecked(ChunkPos chunk, int sectorMinX, int sectorMinZ, BitSet sectorChecked, int sectorWidth) {
        int x = chunk.x - sectorMinX;
        int z = chunk.z - sectorMinZ;
        if (x < 0 || z < 0 || x >= sectorWidth) return false;

        int index = z * sectorWidth + x;
        return index < 0 || index >= sectorChecked.size() || !sectorChecked.get(index);
    }

    private void setSectorChunkChecked(ChunkPos chunk, int sectorMinX, int sectorMinZ, BitSet sectorChecked, int sectorWidth) {
        int x = chunk.x - sectorMinX;
        int z = chunk.z - sectorMinZ;
        if (x < 0 || z < 0 || x >= sectorWidth) return;

        int index = z * sectorWidth + x;
        if (index >= 0 && index < sectorChecked.size()) {
            sectorChecked.set(index);
        }
    }

    /**
     * 检查潜在的结构中心（简化版）
     * 在隐世之境中，所有区块都是樱花林，无需验证生物群系
     */
    private BlockPos checkPotentialCenter(ServerLevel level, ChunkPos centerChunk) {
        // 直接验证结构是否存在，无需检查生物群系
        // 因为在隐世之境中所有区块都是樱花林生物群系
        return verifyStructureExists(level, centerChunk);
    }

    private BlockPos verifyStructureExists(ServerLevel level, ChunkPos chunk) {
        try {
            HolderSet<Structure> structureSet = getOrInitStructureSet(level);
            if (structureSet == null) {
                return null;
            }

            BlockPos chunkCenter = new BlockPos(
                chunk.getMinBlockX() + SearchConfig.CHUNK_CENTER_OFFSET,
                SearchConfig.DEFAULT_STRUCTURE_Y,
                chunk.getMinBlockZ() + SearchConfig.CHUNK_CENTER_OFFSET
            );

            return STRUCTURE_CHECK_LOCK.executeWithLock(level, () -> {
                var result = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level,
                    structureSet,
                    chunkCenter,
                    1,
                    false
                );
                return result != null ? result.getFirst() : null;
            });
        } catch (Exception e) {
            synchronized (STRUCTURE_SET_LOCK) {
                cachedStructureSet = null;
            }
            Global.LOGGER.debug("Structure verification failed, cache cleared", e);
            return null;
        }
    }

    private static HolderSet<Structure> getOrInitStructureSet(ServerLevel level) {
        if (cachedStructureSet == null) {
            synchronized (STRUCTURE_SET_LOCK) {
                if (cachedStructureSet == null) {
                    try {
                        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                        Holder<Structure> structureHolder = structureRegistry.getHolderOrThrow(HIDDEN_RETREAT_KEY);
                        cachedStructureSet = HolderSet.direct(structureHolder);
                        Global.LOGGER.debug("Initialized structure set for hidden_retreat");
                    } catch (Exception e) {
                        Global.LOGGER.error("Failed to initialize hidden_retreat structure set", e);
                        return null;
                    }
                }
            }
        }
        return cachedStructureSet;
    }

    /**
     * 清空缓存
     */
    public static void clearCaches() {
        synchronized (STRUCTURE_SET_LOCK) {
            cachedStructureSet = null;
        }
    }
}
