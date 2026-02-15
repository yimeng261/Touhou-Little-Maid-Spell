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
import java.util.Map;
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
    
    // 高优先级生成验证线程池（单线程，专门用于验证"计划生成"的结构）
    private static final ThreadPoolExecutor GENERATION_VERIFICATION_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(
        1,
        r -> {
            Thread t = new Thread(r, "StructureGenerationVerifier-" + System.currentTimeMillis());
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY); // 最高优先级
            return t;
        }
    );
    
    // 待验证的结构生成任务（worldSeed -> Future）
    private static final Map<Long, CompletableFuture<Boolean>> PENDING_VERIFICATIONS = new ConcurrentHashMap<>();

    public final SearchCacheManager cacheManager;
    
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
     * 注意：缓存检查已在 searchAsync 中完成，此处不再重复检查
     */
    private BlockPos searchParallel(ServerLevel level, BlockPos playerPos) {
        // 1. 检查该维度是否已经生成过结构（通过 GENERATED_DIMENSIONS 集合）
        long worldSeed = level.getSeed();
        ChunkPos searchCenter;
        
        if (HiddenRetreatStructure.GENERATED_DIMENSIONS.contains(worldSeed)) {
            Global.LOGGER.info("检测到维度 {} 已生成隐世之境结构，执行快速定位", level.dimension().location());
            // 结构已生成，从世界出生点开始搜索，因为结构位置是确定性的
            searchCenter = new ChunkPos(level.getSharedSpawnPos());
        } else {
            searchCenter = new ChunkPos(playerPos);
        }
        
        // 2. 执行并行搜索
        BlockPos result = parallelSquareSearch(level, searchCenter);
        
        // 3. 如果未找到结果，但有正在进行的验证任务，等待验证完成
        if (result == null) {
            result = waitForPendingVerification(level);
        }
        
        // 4. 将结果加入缓存
        cacheManager.updateCache(level, result);
        
        return result;
    }
    
    /**
     * 等待正在进行的验证任务完成
     * @param level 服务器世界
     * @return 验证成功后的结构位置，如果验证失败或超时则返回null
     */
    private BlockPos waitForPendingVerification(ServerLevel level) {
        long worldSeed = level.getSeed();
        CompletableFuture<Boolean> verificationTask = PENDING_VERIFICATIONS.get(worldSeed);
        
        if (verificationTask != null && !verificationTask.isDone()) {
            Global.LOGGER.info("等待结构生成验证完成 - 维度: {}", level.dimension().location());
            try {
                // 等待验证完成（最多等待10秒，因为结构生成可能需要较长时间）
                Boolean verified = verificationTask.get(10000, TimeUnit.MILLISECONDS);
                if (verified != null && verified) {
                    // 验证成功，从缓存获取结果
                    SearchCacheManager.CacheCheckResult cacheResult = cacheManager.checkCache(level);
                    if (cacheResult.hasCache && !cacheResult.isNegativeCache) {
                        Global.LOGGER.info("验证完成，获取到结构位置: {}", cacheResult.structurePos);
                        return cacheResult.structurePos;
                    }
                }
            } catch (TimeoutException e) {
                Global.LOGGER.warn("等待验证任务超时（10秒） - 维度: {}", level.dimension().location());
                // 超时后再次检查缓存，可能验证线程刚好完成
                SearchCacheManager.CacheCheckResult cacheResult = cacheManager.checkCache(level);
                if (cacheResult.hasCache && !cacheResult.isNegativeCache) {
                    Global.LOGGER.info("超时后检查缓存，找到结构位置: {}", cacheResult.structurePos);
                    return cacheResult.structurePos;
                }
            } catch (Exception e) {
                Global.LOGGER.error("等待验证任务异常 - 维度: {}", level.dimension().location(), e);
            }
        }
        
        return null;
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
        // 统一的提前终止检查
        if (layerComplete.get() || shouldStopSearch(level)) {
            return null;
        }
        
        return searchSectorComplete(level, centerChunk, sectorX, sectorZ, checkedChunks, totalSearchArea, sectorIndex, totalSectors);
    }
    
    private BlockPos searchSectorComplete(ServerLevel level, ChunkPos centerChunk, int sectorX, int sectorZ,
                                         AtomicInteger checkedChunks, int totalSearchArea, int sectorIndex, int totalSectors) {
        // 扇区边界已在 searchSectorWithTermination 中检查，此处无需重复
        
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
        // 统一的终止检查（线程中断或搜索完成）
        if (Thread.currentThread().isInterrupted() || shouldStopSearch(level)) {
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

        // 螺旋搜索：上边
        for (int x = -layer; x <= layer; x += SearchConfig.SEARCH_STEP) {
            BlockPos result = checkAndSearchCandidate(level, sectorCenter, x, layer, 
                minX, maxX, minZ, maxZ, sectorChecked, sectorWidth, sectorHeight);
            if (result != null) return result;
        }
        
        // 右边
        for (int z = layer - SearchConfig.SEARCH_STEP; z >= -layer; z -= SearchConfig.SEARCH_STEP) {
            BlockPos result = checkAndSearchCandidate(level, sectorCenter, layer, z, 
                minX, maxX, minZ, maxZ, sectorChecked, sectorWidth, sectorHeight);
            if (result != null) return result;
        }
        
        // 下边
        for (int x = layer - SearchConfig.SEARCH_STEP; x >= -layer; x -= SearchConfig.SEARCH_STEP) {
            BlockPos result = checkAndSearchCandidate(level, sectorCenter, x, -layer, 
                minX, maxX, minZ, maxZ, sectorChecked, sectorWidth, sectorHeight);
            if (result != null) return result;
        }
        
        // 左边
        for (int z = -layer + SearchConfig.SEARCH_STEP; z <= layer - SearchConfig.SEARCH_STEP; z += SearchConfig.SEARCH_STEP) {
            BlockPos result = checkAndSearchCandidate(level, sectorCenter, -layer, z, 
                minX, maxX, minZ, maxZ, sectorChecked, sectorWidth, sectorHeight);
            if (result != null) return result;
        }

        return null;
    }
    
    /**
     * 检查并搜索候选区块（提取的工具方法，避免重复代码）
     */
    private BlockPos checkAndSearchCandidate(ServerLevel level, ChunkPos sectorCenter, 
                                            int offsetX, int offsetZ,
                                            int minX, int maxX, int minZ, int maxZ,
                                            BitSet sectorChecked, int sectorWidth, int sectorHeight) {
        // 快速失败检查
        if (Thread.currentThread().isInterrupted() || shouldStopSearch(level)) {
            return null;
        }
        
        ChunkPos candidate = new ChunkPos(sectorCenter.x + offsetX, sectorCenter.z + offsetZ);
        if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
            if (isSectorChunkUnchecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight)) {
                setSectorChunkChecked(candidate, minX, minZ, sectorChecked, sectorWidth, sectorHeight);
                return checkPotentialCenter(level, candidate);
            }
        }
        return null;
    }
    
    /**
     * 检查是否应该停止搜索（结构已找到或缓存已更新）
     */
    private boolean shouldStopSearch(ServerLevel level) {
        // 检查缓存
        SearchCacheManager.CacheCheckResult cacheCheck = cacheManager.checkCache(level);
        if (cacheCheck.hasCache) {
            return true;
        }
        
        // 检查维度标记
        long worldSeed = level.getSeed();
        if (HiddenRetreatStructure.GENERATED_DIMENSIONS.contains(worldSeed)) {
            // 如果有正在进行的验证任务，短暂等待其完成
            CompletableFuture<Boolean> verificationTask = PENDING_VERIFICATIONS.get(worldSeed);
            if (verificationTask != null && !verificationTask.isDone()) {
                try {
                    // 短暂等待（最多500毫秒），不阻塞太久
                    verificationTask.get(500, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // 500ms后仍未完成，不等了，让主搜索流程去等待
                } catch (Exception e) {
                    // 其他异常，继续
                }
            }
            return true;
        }
        
        return false;
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
     * 检查潜在的结构中心
     */
    private BlockPos checkPotentialCenter(ServerLevel level, ChunkPos centerChunk) {
        // 在验证之前先检查缓存，避免不必要的区块生成
        SearchCacheManager.CacheCheckResult cacheCheck = cacheManager.checkCache(level);
        if (cacheCheck.hasCache) {
            return cacheCheck.structurePos; // 可能是正缓存或负缓存
        }
        
        // 直接验证结构是否存在（已生成和未生成的情况统一处理）
        return verifyStructureExists(level, centerChunk);
    }
    
    /**
     * 创建区块中心位置
     */
    private BlockPos createChunkCenter(ChunkPos chunk) {
        return new BlockPos(
            chunk.getMinBlockX() + SearchConfig.CHUNK_CENTER_OFFSET, 
            SearchConfig.DEFAULT_STRUCTURE_Y, 
            chunk.getMinBlockZ() + SearchConfig.CHUNK_CENTER_OFFSET
        );
    }
    
    /**
     * 验证结构是否存在（统一处理已生成和未生成的情况）
     */
    private BlockPos verifyStructureExists(ServerLevel level, ChunkPos chunk) {
        try {
            HolderSet<Structure> structureSet = getOrInitStructureSet(level);
            if (structureSet == null) {
                return null;
            }
            
            BlockPos chunkCenter = createChunkCenter(chunk);
            long worldSeed = level.getSeed();
            // 使用独立的验证线程池，避免与搜索任务争抢线程导致死锁
            CompletableFuture<BlockPos> verificationFuture = CompletableFuture.supplyAsync(() -> 
                STRUCTURE_CHECK_LOCK.executeWithLock(level, () -> {
                    // 再次检查缓存（可能在等待锁的过程中其他线程已找到）

                    SearchCacheManager.CacheCheckResult cacheCheck = cacheManager.checkCache(level);
                    if (cacheCheck.hasCache) {
                        return cacheCheck.structurePos;
                    }
                    
                    // 未生成：强制加载区块触发生成，然后小范围搜索
                    level.getChunk(chunk.x, chunk.z);
                    var structureResult = level.getChunkSource().getGenerator().findNearestMapStructure(
                        level, structureSet, chunkCenter, 1, false
                    );
                    return structureResult != null ? structureResult.getFirst() : null;
                }), 
                VERIFICATION_EXECUTOR
            );
            
            BlockPos result;
            try {
                result = verificationFuture.get(SearchConfig.STRUCTURE_VERIFICATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                verificationFuture.cancel(true);
                return null;
            } catch (Exception e) {
                return null;
            }
            
            if (result != null) {
                // 更新维度标记和缓存
                HiddenRetreatStructure.GENERATED_DIMENSIONS.add(worldSeed);
                cacheManager.updateCache(level, result);
                Global.LOGGER.debug("找到结构，更新缓存 - 维度: {}, 位置: {}", level.dimension().location(), result);
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
    
    /**
     * 提交结构生成验证任务（高优先级）
     * 当结构被标记为"计划生成"后调用此方法，立即验证结构是否真正生成完成
     * 
     * @param level 服务器世界
     * @param expectedPos 预期的结构位置
     */
    public void submitGenerationVerification(ServerLevel level, BlockPos expectedPos) {
        long worldSeed = level.getSeed();
        
        // 如果已经有验证任务在运行，跳过
        if (PENDING_VERIFICATIONS.containsKey(worldSeed)) {
            Global.LOGGER.debug("验证任务已在运行 - 维度: {}", level.dimension().location());
            return;
        }
        
        Global.LOGGER.info("提交结构生成验证任务 - 维度: {}, 预期位置: {}", 
            level.dimension().location(), expectedPos);
        
        // 创建高优先级验证任务
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<Boolean> verificationFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return verifyGeneratedStructure(level, expectedPos);
            } catch (Exception e) {
                Global.LOGGER.error("结构生成验证异常 - 维度: {}", level.dimension().location(), e);
                return false;
            }
        }, GENERATION_VERIFICATION_EXECUTOR).whenComplete((verified, throwable) -> {
            // 验证完成后移除任务
            PENDING_VERIFICATIONS.remove(worldSeed);
            
            long elapsed = System.currentTimeMillis() - startTime;
            if (verified != null && verified) {
                Global.LOGGER.info("结构生成验证成功 - 维度: {}, 位置: {}, 耗时: {} ms", 
                    level.dimension().location(), expectedPos, elapsed);
            } else {
                Global.LOGGER.warn("结构生成验证失败或未找到 - 维度: {}, 耗时: {} ms", 
                    level.dimension().location(), elapsed);
            }
        });
        
        PENDING_VERIFICATIONS.put(worldSeed, verificationFuture);
    }
    
    /**
     * 验证结构是否真正生成完成（高优先级线程执行）
     * 使用重试机制，因为结构生成是异步的，可能需要多次检查
     * 
     * @param level 服务器世界
     * @param expectedPos 预期的结构位置
     * @return true 如果结构已生成完成，false 如果还未完成
     */
    private boolean verifyGeneratedStructure(ServerLevel level, BlockPos expectedPos) {
        try {
            HolderSet<Structure> structureSet = getOrInitStructureSet(level);
            if (structureSet == null) {
                return false;
            }
            
            // 重试配置：结构生成可能需要时间，最多尝试10次
            final int maxRetries = 5;
            final long retryIntervalMs = 500; // 每次重试间隔500ms
            
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                final int currentAttempt = attempt;
                
                // 使用加锁的方式进行结构验证，确保线程安全
                Boolean verifyResult = STRUCTURE_CHECK_LOCK.executeWithLock(level, () -> {
                    // 先检查缓存（可能其他搜索线程已经找到）
                    SearchCacheManager.CacheCheckResult cacheCheck = cacheManager.checkCache(level);
                    if (cacheCheck.hasCache && !cacheCheck.isNegativeCache) {
                        Global.LOGGER.info("高优先级验证：从缓存获取结果（第{}次尝试） - 位置: {}", 
                            currentAttempt, cacheCheck.structurePos);
                        
                        // 确保维度标记已更新
                        long worldSeed = level.getSeed();
                        HiddenRetreatStructure.GENERATED_DIMENSIONS.add(worldSeed);
                        return true;
                    }
                    
                    // 使用小范围搜索验证结构是否存在
                    // 强制加载区块以确保结构生成流程完成
                    ChunkPos targetChunk = new ChunkPos(expectedPos);
                    level.getChunk(targetChunk.x, targetChunk.z);
                    
                    // 短暂等待，确保 StructureStart 已完全存储和索引
                    // 这解决了区块加载完成到结构可查询之间的时间差
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    
                    var structureResult = level.getChunkSource().getGenerator().findNearestMapStructure(
                        level, structureSet, expectedPos, 4, false
                    );
                    
                    if (structureResult != null) {
                        BlockPos foundPos = structureResult.getFirst();
                        
                        // 验证找到的位置是否与预期位置接近（允许一定偏差）
                        if (foundPos != null && foundPos.distSqr(expectedPos) < 256 * 256) {
                            // 更新缓存，确保后续搜索能快速找到
                            cacheManager.updateCache(level, foundPos);
                            
                            // 双重确认：检查 GENERATED_DIMENSIONS 是否已标记
                            long worldSeed = level.getSeed();
                            HiddenRetreatStructure.GENERATED_DIMENSIONS.add(worldSeed);
                            
                            Global.LOGGER.info("高优先级验证成功（第{}次尝试）：结构已生成 - 位置: {}", 
                                currentAttempt, foundPos);
                            return true;
                        }
                    }
                    return false;
                });
                
                if (verifyResult != null && verifyResult) {
                    return true;
                }
                
                // 如果不是最后一次尝试，等待后重试
                if (attempt < maxRetries) {
                    Global.LOGGER.debug("高优先级验证（第{}次尝试）：结构尚未生成，{}ms后重试 - 预期位置: {}", 
                        attempt, retryIntervalMs, expectedPos);
                    try {
                        Thread.sleep(retryIntervalMs);
                    } catch (InterruptedException e) {
                        Global.LOGGER.warn("验证等待被中断");
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            
            Global.LOGGER.warn("高优先级验证失败（已尝试{}次）：结构未生成 - 预期位置: {}", 
                maxRetries, expectedPos);
            return false;
            
        } catch (Exception e) {
            Global.LOGGER.error("验证结构生成时发生异常", e);
            return false;
        }
    }
    
    /**
     * 获取当前待验证任务数量（用于监控和调试）
     */
    public int getPendingVerificationCount() {
        return PENDING_VERIFICATIONS.size();
    }
    
    /**
     * 清理所有待验证任务（用于服务器关闭时）
     */
    public void cancelAllPendingVerifications() {
        PENDING_VERIFICATIONS.forEach((seed, task) -> {
            if (task != null && !task.isDone()) {
                task.cancel(false);
            }
        });
        PENDING_VERIFICATIONS.clear();
        Global.LOGGER.info("已清理所有待验证的结构生成任务");
    }

}
