package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 结构搜索引擎
 * 负责执行并行的隐世之境结构搜索
 */
public class StructureSearchEngine {
    
    // 结构验证优化：提取静态常量避免重复创建
    @SuppressWarnings("removal")
    private static final ResourceLocation HIDDEN_RETREAT_LOCATION =
        new ResourceLocation("touhou_little_maid_spell", "hidden_retreat");
    private static final ResourceKey<Structure> HIDDEN_RETREAT_KEY =
        ResourceKey.create(Registries.STRUCTURE, HIDDEN_RETREAT_LOCATION);
    
    // 结构集合缓存：延迟初始化以避免早期加载问题
    private static volatile HolderSet<Structure> cachedStructureSet = null;
    private static final Object STRUCTURE_SET_LOCK = new Object();
    
    // 结构检查分段锁
    private static final StripedLock STRUCTURE_CHECK_LOCK = new StripedLock(SearchConfig.LOCK_STRIPE_COUNT);
    
    // 搜索线程池（3/4线程用于搜索扇区）
    private static final ThreadPoolExecutor SEARCH_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(
        SearchConfig.getRecommendedThreadPoolSize(),
        r -> {
            Thread t = new Thread(r, SearchConfig.THREAD_NAME_PREFIX + System.currentTimeMillis());
            t.setDaemon(true);
            t.setPriority(SearchConfig.THREAD_PRIORITY);
            return t;
        }
    );
    
    // 验证线程池（1/4线程专门用于结构验证，避免线程池饥饿）
    private static final ThreadPoolExecutor VERIFICATION_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(
        SearchConfig.getRecommendedVerificationThreadPoolSize(),
        r -> {
            Thread t = new Thread(r, SearchConfig.VERIFICATION_THREAD_NAME_PREFIX + System.currentTimeMillis());
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
            if (cacheResult.isNegativeCache) {
                // 负缓存：之前搜索过但未找到，直接返回null避免重复搜索
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(cacheResult.structurePos);
        }
        
        // 2. 检查是否已有相同维度的搜索在进行（使用维度级别的键）
        String searchKey = cacheManager.generateDimensionKey(level);
        CompletableFuture<BlockPos> existingSearch = cacheManager.getOngoingSearch(searchKey);
        if (existingSearch != null) {
            return existingSearch;
        }
        
        // 3. 启动新的异步搜索
        Global.LOGGER.info("开始结构搜索 - 维度: {}", level.dimension().location());
        
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
                Global.LOGGER.info("结构搜索完成 - 找到位置: {}", result);
            } else {
                Global.LOGGER.warn("结构搜索完成 - 未找到结构");
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
            // 包括正缓存和负缓存
            return cacheResult.structurePos; // 负缓存时为null
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
        int maxSectorLayer = (SearchConfig.MAX_SEARCH_RADIUS + SearchConfig.SECTOR_SIZE - 1) / SearchConfig.SECTOR_SIZE;
        
        // 计算总搜索范围
        int totalSearchArea = (2 * SearchConfig.MAX_SEARCH_RADIUS + 1) * (2 * SearchConfig.MAX_SEARCH_RADIUS + 1);
        AtomicInteger checkedChunks = new AtomicInteger(0);
        
        long searchStartTime = System.currentTimeMillis();
        
        for (int sectorLayer = 0; sectorLayer <= maxSectorLayer; sectorLayer++) {
            BlockPos result = searchSectorLayerParallel(level, centerChunk, sectorLayer, checkedChunks, totalSearchArea);
            if (result != null) {
                long searchTime = System.currentTimeMillis() - searchStartTime;
                Global.LOGGER.info("找到结构 - 位置: {}, 耗时: {} ms", result, searchTime);
                return result;
            }
        }
        
        long searchTime = System.currentTimeMillis() - searchStartTime;
        Global.LOGGER.warn("搜索完成但未找到结构 - 耗时: {} ms", searchTime);
        
        return null;
    }
    
    /**
     * 多线程并行搜索指定小方格层
     * 使用 CompletableFuture.anyOf 替代忙等轮询，提高效率
     */
    private BlockPos searchSectorLayerParallel(ServerLevel level, ChunkPos centerChunk, int sectorLayer, 
                                              AtomicInteger checkedChunks, int totalSearchArea) {
        List<int[]> sectorCoords = generateSectorCoordinates(sectorLayer);
        List<CompletableFuture<BlockPos>> sectorTasks = new ArrayList<>();
        AtomicBoolean layerComplete = new AtomicBoolean(false);
        AtomicInteger completedSectors = new AtomicInteger(0);
        int totalSectors = sectorCoords.size();

        for (final int[] coords : sectorCoords) {
            final int sectorX = coords[0];
            final int sectorZ = coords[1];
            final int sectorIndex = sectorTasks.size();

            CompletableFuture<BlockPos> sectorTask = CompletableFuture.supplyAsync(() -> {
                BlockPos result = searchSectorWithTermination(level, centerChunk, sectorX, sectorZ, 
                    layerComplete, checkedChunks, totalSearchArea, sectorIndex, totalSectors);
                if (result != null) {
                    completedSectors.incrementAndGet();
                }
                return result;
            }, SEARCH_EXECUTOR);

            sectorTasks.add(sectorTask);
        }
        
        try {
            // 使用 anyOf 等待任一任务完成，避免忙等轮询
            BlockPos result = null;
            
            while (!sectorTasks.isEmpty() && result == null) {
                // 等待任一任务完成
                CompletableFuture<Object> anyCompleted = CompletableFuture.anyOf(
                    sectorTasks.toArray(new CompletableFuture[0])
                );
                
                try {
                    anyCompleted.get(120, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // 忽略，继续检查已完成的任务
                }
                
                // 检查所有已完成的任务
                List<CompletableFuture<BlockPos>> remainingTasks = new ArrayList<>();
                for (CompletableFuture<BlockPos> task : sectorTasks) {
                    if (task.isDone()) {
                        try {
                            BlockPos taskResult = task.get();
                            if (taskResult != null && result == null) {
                                result = taskResult;
                                layerComplete.set(true);
                            }
                        } catch (Exception e) {
                            // 忽略单个任务的异常
                        }
                    } else {
                        remainingTasks.add(task);
                    }
                }
                sectorTasks = remainingTasks;
                
                // 如果找到结果，取消剩余任务
                if (result != null) {
                    for (CompletableFuture<BlockPos> task : sectorTasks) {
                        task.cancel(true);
                    }
                    break;
                }
            }
            
            return result;
            
        } catch (Exception e) {
            layerComplete.set(true);
            sectorTasks.forEach(task -> task.cancel(true));
            return null;
        }
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
                                                 AtomicBoolean layerComplete, AtomicInteger checkedChunks, 
                                                 int totalSearchArea, int sectorIndex, int totalSectors) {
        if (layerComplete.get()) {
            return null;
        }
        
        return searchSectorComplete(level, centerChunk, sectorX, sectorZ, checkedChunks, totalSearchArea, sectorIndex, totalSectors);
    }
    
    private BlockPos searchSectorComplete(ServerLevel level, ChunkPos centerChunk, int sectorX, int sectorZ,
                                         AtomicInteger checkedChunks, int totalSearchArea, int sectorIndex, int totalSectors) {
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
        int sectorAreaChunks = sectorWidth * sectorHeight;
        BitSet sectorChecked = new BitSet(sectorAreaChunks);
        
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
        // 检查线程中断状态，支持任务取消
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        
        int sectorWidth = maxX - minX + 1;
        int sectorHeight = maxZ - minZ + 1;
        
        if (layer == 0) {
            if (isInSectorBounds(sectorCenter, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(sectorCenter, minX, minZ, sectorChecked, sectorWidth, sectorHeight)) {
                    setSectorChunkChecked(sectorCenter, minX, minZ, sectorChecked, sectorWidth, sectorHeight);
                    return checkPotentialCenter(level, sectorCenter);
                }
            }
            return null;
        }

        for (int x = -layer; x <= layer; x += SearchConfig.SEARCH_STEP) {
            if (Thread.currentThread().isInterrupted()) return null;
            ChunkPos candidate = new ChunkPos(sectorCenter.x + x, sectorCenter.z + layer);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight);
                    BlockPos result = checkPotentialCenter(level, candidate);
                    if (result != null) return result;
                }
            }
        }
        
        for (int z = layer - SearchConfig.SEARCH_STEP; z >= -layer; z -= SearchConfig.SEARCH_STEP) {
            if (Thread.currentThread().isInterrupted()) return null;
            ChunkPos candidate = new ChunkPos(sectorCenter.x + layer, sectorCenter.z + z);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight);
                    BlockPos result = checkPotentialCenter(level, candidate);
                    if (result != null) return result;
                }
            }
        }
        
        for (int x = layer - SearchConfig.SEARCH_STEP; x >= -layer; x -= SearchConfig.SEARCH_STEP) {
            if (Thread.currentThread().isInterrupted()) return null;
            ChunkPos candidate = new ChunkPos(sectorCenter.x + x, sectorCenter.z - layer);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight);
                    BlockPos result = checkPotentialCenter(level, candidate);
                    if (result != null) return result;
                }
            }
        }
        
        for (int z = -layer + SearchConfig.SEARCH_STEP; z <= layer - SearchConfig.SEARCH_STEP; z += SearchConfig.SEARCH_STEP) {
            if (Thread.currentThread().isInterrupted()) return null;
            ChunkPos candidate = new ChunkPos(sectorCenter.x - layer, sectorCenter.z + z);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight);
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
    
    private boolean isSectorChunkUnchecked(ChunkPos chunk, int sectorMinX, int sectorMinZ, BitSet sectorChecked, int sectorWidth, int sectorHeight) {
        int x = chunk.x - sectorMinX;
        int z = chunk.z - sectorMinZ;
        // 边界检查：超出范围的区块视为未检查（需要检查）
        if (x < 0 || z < 0 || x >= sectorWidth || z >= sectorHeight) return false;
        
        int index = z * sectorWidth + x;
        int sectorAreaChunks = sectorWidth * sectorHeight;
        // 使用容量而不是size()：BitSet.size()返回最高设置位+1，初始为0会导致所有区块被跳过
        if (index < 0 || index >= sectorAreaChunks) return false;
        return !sectorChecked.get(index);
    }
    
    private void setSectorChunkChecked(ChunkPos chunk, int sectorMinX, int sectorMinZ, BitSet sectorChecked, int sectorWidth, int sectorHeight) {
        int x = chunk.x - sectorMinX;
        int z = chunk.z - sectorMinZ;
        if (x < 0 || z < 0 || x >= sectorWidth || z >= sectorHeight) return;
        
        int index = z * sectorWidth + x;
        int sectorAreaChunks = sectorWidth * sectorHeight;
        if (index >= 0 && index < sectorAreaChunks) {
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
            
            // 使用独立的验证线程池，避免与搜索任务争抢线程导致死锁
            CompletableFuture<BlockPos> verificationFuture = CompletableFuture.supplyAsync(() -> STRUCTURE_CHECK_LOCK.executeWithLock(level, () -> {
                var structureResult = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level,
                    structureSet,
                    chunkCenter,
                    2,
                    false
                );
                return structureResult != null ? structureResult.getFirst() : null;
            }), VERIFICATION_EXECUTOR);
            
            BlockPos result;
            try {
                // 设置超时，避免永久阻塞
                result = verificationFuture.get(SearchConfig.STRUCTURE_VERIFICATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                verificationFuture.cancel(true);
                return null;
            } catch (Exception e) {
                return null;
            }
            
            if (result != null) {
                HiddenRetreatStructure.GENERATED_DIMENSIONS.add(level.getSeed());
            }
            
            return result;
        } catch (Exception e) {
            synchronized (STRUCTURE_SET_LOCK) {
                cachedStructureSet = null;
            }
            Global.LOGGER.error("结构验证失败", e);
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
                    } catch (Exception e) {
                        Global.LOGGER.error("初始化结构集失败", e);
                        return null;
                    }
                }
            }
        }
        return cachedStructureSet;
    }

}
