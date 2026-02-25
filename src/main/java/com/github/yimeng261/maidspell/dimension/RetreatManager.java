package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

/**
 * 归隐之地统一状态管理器
 * 收归所有散落在各类中的 static 状态，提供统一的生命周期管理。
 *
 * 管理内容：
 * - 玩家维度缓存 (playerRetreats)
 * - 维度注册表 (dimensionRegistry, ResourceKey-based)
 * - 已生成结构标记 (generatedDimensions)
 * - 结构位置缓存 (structureCache)
 * - 简化的搜索执行器 (单线程异步 findNearestMapStructure)
 */
public class RetreatManager {

    // ========== 维度注册 ==========

    /** 维度 ResourceKey → ServerLevel 映射（替代原 DIMENSIONS_MAP<Long, ServerLevel>） */
    private static final Map<ResourceKey<Level>, ServerLevel> dimensionRegistry = new ConcurrentHashMap<>();

    /** 已生成隐世之境结构的维度集合（替代原 GENERATED_DIMENSIONS<Long>） */
    private static final Set<ResourceKey<Level>> generatedDimensions = ConcurrentHashMap.newKeySet();

    /** 玩家 UUID → ServerLevel 缓存（原 PlayerRetreatManager.playerRetreats） */
    private static final Map<UUID, ServerLevel> playerRetreats = new ConcurrentHashMap<>();

    // ========== 结构缓存 ==========

    /** 玩家 UUID → 结构位置缓存（私人/共享模式均适用，每个玩家独立缓存自己的隐世之境位置） */
    private static final Map<UUID, CacheEntry> structureCache = new ConcurrentHashMap<>();

    /** 未分配结构池（共享维度专用）：维度 seed → 结构位置列表 */
    private static final Map<Long, Queue<BlockPos>> unassignedStructures = new ConcurrentHashMap<>();

    /** 强制加载的结构区块：维度 ResourceKey → 区块位置集合 */
    private static final Map<ResourceKey<Level>, Set<net.minecraft.world.level.ChunkPos>> forceLoadedChunks = new ConcurrentHashMap<>();

    /** 负缓存 TTL：5 分钟 */
    private static final long NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000;

    private static class CacheEntry {
        final BlockPos position; // null = 负缓存
        final long timestamp;

        CacheEntry(BlockPos position) {
            this.position = position;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpiredNegative() {
            return position == null && (System.currentTimeMillis() - timestamp) > NEGATIVE_CACHE_TTL_MS;
        }
    }

    // ========== 搜索执行器（单线程） ==========

    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RetreatStructureSearch");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    /** 正在进行的搜索（维度 key → Future），防止重复搜索 */
    private static final Map<String, CompletableFuture<BlockPos>> ongoingSearches = new ConcurrentHashMap<>();

    /** 搜索队列清理调度器 */
    private static ScheduledExecutorService cleanupScheduler = null;

    /** 结构 HolderSet 缓存（延迟初始化） */
    private static volatile HolderSet<Structure> cachedStructureSet = null;
    private static final Object STRUCTURE_SET_LOCK = new Object();

    @SuppressWarnings("removal")
    private static final ResourceKey<Structure> HIDDEN_RETREAT_KEY =
        ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation("touhou_little_maid_spell", "hidden_retreat"));

    // ========== 生命周期 ==========

    /** 服务器启动时调用 */
    public static void init() {
        clearAll();
        // 启动队列清理调度器（每分钟清理超时请求）
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StructureSearchQueue-Cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(
            StructureSearchQueue::cleanupExpiredRequests,
            60, 60, TimeUnit.SECONDS
        );
        MaidSpellMod.LOGGER.info("RetreatManager initialized");
    }

    /** 服务器关闭时调用 */
    public static void shutdown() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            cleanupScheduler = null;
        }
        // 取消所有进行中的搜索
        ongoingSearches.values().forEach(f -> f.cancel(true));
        // 清理结构去重记录
        com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure.cleanupProcessedStructures("");
        clearAll();
        MaidSpellMod.LOGGER.info("RetreatManager shutdown");
    }

    private static void clearAll() {
        dimensionRegistry.clear();
        generatedDimensions.clear();
        playerRetreats.clear();
        structureCache.clear();
        ongoingSearches.clear();
        unassignedStructures.clear();
        forceLoadedChunks.clear();
        StructureSearchQueue.clearAll();
        cachedStructureSet = null;
    }

    // ========== 维度注册 API ==========

    public static void registerDimension(ResourceKey<Level> key, ServerLevel level) {
        dimensionRegistry.put(key, level);
        MaidSpellMod.LOGGER.debug("Registered dimension: {}", key.location());
    }

    public static void unregisterDimension(ResourceKey<Level> key) {
        ServerLevel level = dimensionRegistry.remove(key);
        generatedDimensions.remove(key);
        
        // 清理该维度对应玩家的结构缓存（通过 playerRetreats 反查 UUID）
        playerRetreats.forEach((uuid, dimLevel) -> {
            if (dimLevel.dimension().equals(key)) {
                structureCache.remove(uuid);
            }
        });
        
        // 清理强制加载的区块
        Set<net.minecraft.world.level.ChunkPos> forcedChunks = forceLoadedChunks.remove(key);
        if (forcedChunks != null && level != null) {
            for (net.minecraft.world.level.ChunkPos chunkPos : forcedChunks) {
                level.setChunkForced(chunkPos.x, chunkPos.z, false);
            }
            MaidSpellMod.LOGGER.info("清理维度强制加载区块 - 维度: {}, 数量: {}", key.location(), forcedChunks.size());
        }
        
        // 清理结构去重记录（使用前缀匹配）
        String dimPrefix = key.location() + "@";
        com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure.cleanupProcessedStructures(dimPrefix);
        MaidSpellMod.LOGGER.debug("Unregistered dimension: {}", key.location());
    }

    public static boolean isDimensionRegistered(ResourceKey<Level> key) {
        return dimensionRegistry.containsKey(key);
    }

    @Nullable
    public static ServerLevel getDimensionLevel(ResourceKey<Level> key) {
        return dimensionRegistry.get(key);
    }

    /** 通过 seed 查找维度（兼容 generate() 中只有 seed 的场景） */
    @Nullable
    public static ResourceKey<Level> findDimensionKeyBySeed(long seed) {
        for (Map.Entry<ResourceKey<Level>, ServerLevel> entry : dimensionRegistry.entrySet()) {
            if (entry.getValue().getSeed() == seed) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ========== 结构生成标记 ==========

    public static void markStructureGenerated(ResourceKey<Level> key) {
        generatedDimensions.add(key);
    }

    public static boolean isStructureGenerated(ResourceKey<Level> key) {
        return generatedDimensions.contains(key);
    }

    /**
     * 原子性地标记维度已生成结构（用于 ChunkGeneratorMixin 防止重复生成）。
     * 基于 ConcurrentHashMap.newKeySet().add() 的原子语义：
     * 同一时刻只有第一个调用者能得到 true，后续调用者得到 false。
     *
     * @return true = 首次标记，允许本次生成；false = 已被标记，应拦截本次生成
     */
    public static boolean tryMarkStructureGenerated(ResourceKey<Level> key) {
        return generatedDimensions.add(key);
    }

    // ========== 玩家维度缓存 ==========

    public static void cachePlayerRetreat(UUID playerUUID, ServerLevel level) {
        playerRetreats.put(playerUUID, level);
    }

    @Nullable
    public static ServerLevel getCachedPlayerRetreat(UUID playerUUID) {
        return playerRetreats.get(playerUUID);
    }

    public static void removeCachedPlayerRetreat(UUID playerUUID) {
        playerRetreats.remove(playerUUID);
    }

    public static void clearPlayerRetreatCache() {
        playerRetreats.clear();
    }

    public static int getCachedPlayerRetreatCount() {
        return playerRetreats.size();
    }

    // ========== 结构缓存 API ==========

    public static CacheResult checkCache(UUID playerUUID) {
        CacheEntry entry = structureCache.get(playerUUID);
        if (entry == null) {
            return CacheResult.NO_CACHE;
        }
        if (entry.isExpiredNegative()) {
            structureCache.remove(playerUUID);
            return CacheResult.NO_CACHE;
        }
        if (entry.position != null) {
            return CacheResult.found(entry.position);
        }
        return CacheResult.NEGATIVE;
    }

    public static void updateCache(UUID playerUUID, @Nullable BlockPos pos) {
        structureCache.put(playerUUID, new CacheEntry(pos));
    }

    // ========== 未分配结构池 API ==========

    /**
     * 将结构加入未分配池（结构自然生成但队列为空时调用）
     * @param worldSeed 维度种子
     * @param structurePos 结构位置
     */
    public static void addUnassignedStructure(long worldSeed, BlockPos structurePos) {
        Queue<BlockPos> pool = unassignedStructures.computeIfAbsent(worldSeed, k -> new ConcurrentLinkedQueue<>());
        pool.add(structurePos);
        MaidSpellMod.LOGGER.info("添加未分配结构到池 - 种子: {}, 位置: {}, 池大小: {}", 
            worldSeed, structurePos, pool.size());
    }

    /**
     * 从未分配池中取出一个结构（玩家搜索时优先调用）
     * @param worldSeed 维度种子
     * @return 结构位置，如果池为空则返回null
     */
    @Nullable
    public static BlockPos pollUnassignedStructure(long worldSeed) {
        Queue<BlockPos> pool = unassignedStructures.get(worldSeed);
        if (pool != null && !pool.isEmpty()) {
            BlockPos pos = pool.poll();
            MaidSpellMod.LOGGER.info("从未分配池取出结构 - 种子: {}, 位置: {}, 剩余: {}", 
                worldSeed, pos, pool.size());
            return pos;
        }
        return null;
    }

    /**
     * 检查未分配池是否有可用结构
     * @param worldSeed 维度种子
     * @return 如果有未分配结构则返回true
     */
    public static boolean hasUnassignedStructures(long worldSeed) {
        Queue<BlockPos> pool = unassignedStructures.get(worldSeed);
        return pool != null && !pool.isEmpty();
    }

    /** 缓存查询结果 */
    public static class CacheResult {
        public static final CacheResult NO_CACHE = new CacheResult(false, false, null);
        public static final CacheResult NEGATIVE = new CacheResult(true, true, null);

        public final boolean hasCache;
        public final boolean isNegative;
        @Nullable public final BlockPos position;

        private CacheResult(boolean hasCache, boolean isNegative, @Nullable BlockPos position) {
            this.hasCache = hasCache;
            this.isNegative = isNegative;
            this.position = position;
        }

        public static CacheResult found(BlockPos pos) {
            return new CacheResult(true, false, pos);
        }
    }

    // ========== 简化搜索 API ==========

    /**
     * 异步搜索隐世之境结构（简化版）。
     * 单线程调用 findNearestMapStructure 触发区块加载和结构生成。
     * 实际结果由 afterPlace 回调通过 StructureSearchQueue 提供。
     */
    public static CompletableFuture<Void> triggerStructureSearch(ServerLevel level, BlockPos playerPos) {
        String dimKey = level.dimension().location().toString();

        // 已有搜索在进行，不重复触发
        if (ongoingSearches.containsKey(dimKey)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<BlockPos> searchFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return doSearch(level, playerPos);
            } catch (Exception e) {
                MaidSpellMod.LOGGER.error("Structure search failed for {}", dimKey, e);
                return null;
            }
        }, SEARCH_EXECUTOR).whenComplete((result, throwable) -> {
            ongoingSearches.remove(dimKey);
            // 注意：result 可能为 null（正常情况），因为结果通过 afterPlace 回调返回
            // 强制加载机制会确保区块生成，如果真的失败会在超时后由 WindSeekingBell 处理
        });

        ongoingSearches.put(dimKey, searchFuture);
        return searchFuture.thenAccept(pos -> {
            // 搜索引擎的返回值被忽略，结果由 afterPlace 回调提供
        });
    }

    /**
     * 实际搜索逻辑：调用 findNearestMapStructure 触发结构生成
     */
    @Nullable
    private static BlockPos doSearch(ServerLevel level, BlockPos playerPos) {
        HolderSet<Structure> structureSet = getOrInitStructureSet(level);
        if (structureSet == null) return null;

        // 确定搜索中心
        BlockPos searchCenter;
        if (Config.enablePrivateDimensions) {
            ResourceKey<Level> dimKey = level.dimension();
            if (generatedDimensions.contains(dimKey)) {
                searchCenter = level.getSharedSpawnPos();
            } else {
                searchCenter = playerPos;
            }
        } else {
            searchCenter = playerPos;
        }

        MaidSpellMod.LOGGER.info("Triggering structure search from {}", searchCenter);

        // 共享模式跳过已知结构，确保每次搜索生成新结构而非返回已分配给其他玩家的结构
        boolean skipKnown = !Config.enablePrivateDimensions;
        var result = level.getChunkSource().getGenerator().findNearestMapStructure(
            level, structureSet, searchCenter, 100, skipKnown
        );

        if (result != null) {
            BlockPos structurePos = result.getFirst();
            MaidSpellMod.LOGGER.info("findNearestMapStructure returned: {}", structurePos);
            
            // 共享模式：检查返回的位置是否已被分配
            if (!Config.enablePrivateDimensions) {
                int maxRetries = 5;
                int retryCount = 0;
                
                while (isStructureAlreadyAssigned(structurePos) && retryCount < maxRetries) {
                    retryCount++;
                    MaidSpellMod.LOGGER.info("位置 {} 已被分配，从该位置继续搜索 (尝试 {}/{})", 
                        structurePos, retryCount, maxRetries);
                    
                    // 从已分配位置继续搜索，findNearestMapStructure 会自然返回下一个结构
                    // 稍微偏移一点避免返回同一个位置
                    BlockPos nextSearchPos = structurePos.offset(16, 0, 16);
                    var retryResult = level.getChunkSource().getGenerator().findNearestMapStructure(
                        level, structureSet, nextSearchPos, 100, skipKnown
                    );
                    
                    if (retryResult != null) {
                        structurePos = retryResult.getFirst();
                        MaidSpellMod.LOGGER.info("搜索到新位置: {}", structurePos);
                    } else {
                        MaidSpellMod.LOGGER.warn("搜索失败，无法找到更多结构");
                        break;
                    }
                }
                
                if (retryCount >= maxRetries) {
                    MaidSpellMod.LOGGER.error("达到最大重试次数，所有找到的位置都已被分配");
                }
            }
            
            // 关键修复：主动加载目标区块，触发结构生成
            forceLoadStructureChunks(level, structurePos);
            
            return structurePos;
        }

        MaidSpellMod.LOGGER.warn("findNearestMapStructure returned null");
        return null;
    }
    
    /**
     * 检查结构位置是否已被分配给其他玩家
     * 使用距离判断（同一结构的理论位置和实际位置可能有偏差）
     * @param structurePos 结构位置
     * @return 如果已分配则返回true
     */
    private static boolean isStructureAlreadyAssigned(BlockPos structurePos) {
        // 结构spacing=10 chunks (160格)，如果距离<80格，视为同一结构
        final int SAME_STRUCTURE_THRESHOLD = 80;
        final int THRESHOLD_SQ = SAME_STRUCTURE_THRESHOLD * SAME_STRUCTURE_THRESHOLD;
        
        // 检查是否在任何玩家的缓存中（忽略Y坐标，只比较XZ平面距离）
        for (CacheEntry entry : structureCache.values()) {
            if (entry.position != null) {
                int dx = entry.position.getX() - structurePos.getX();
                int dz = entry.position.getZ() - structurePos.getZ();
                int distSq = dx * dx + dz * dz;
                
                if (distSq < THRESHOLD_SQ) {
                    MaidSpellMod.LOGGER.debug("检测到已分配结构 - 距离: {} 格, 已分配位置: {}, 搜索位置: {}", 
                        (int)Math.sqrt(distSq), entry.position, structurePos);
                    return true;
                }
            }
        }
        
        // 检查是否在未分配池中
        for (Queue<BlockPos> pool : unassignedStructures.values()) {
            for (BlockPos pos : pool) {
                int dx = pos.getX() - structurePos.getX();
                int dz = pos.getZ() - structurePos.getZ();
                int distSq = dx * dx + dz * dz;
                
                if (distSq < THRESHOLD_SQ) {
                    MaidSpellMod.LOGGER.debug("检测到未分配池中的结构 - 距离: {} 格, 池中位置: {}, 搜索位置: {}", 
                        (int)Math.sqrt(distSq), pos, structurePos);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 强制加载结构所在区块，触发结构首次生成
     * 使用 Minecraft 的 ForceLoad 机制确保未生成的区块会触发世界生成
     * @param level 服务器世界
     * @param structureCenter 结构中心位置
     */
    private static void forceLoadStructureChunks(ServerLevel level, BlockPos structureCenter) {
        net.minecraft.world.level.ChunkPos centerChunk = new net.minecraft.world.level.ChunkPos(structureCenter);
        ResourceKey<Level> dimKey = level.dimension();
        
        // 加载中心区块及周围区块（确保整个结构都能生成）
        int loadRadius = 2;
        long startTime = System.currentTimeMillis();
        
        Set<net.minecraft.world.level.ChunkPos> chunksToLoad = new HashSet<>();
        
        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dz = -loadRadius; dz <= loadRadius; dz++) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(
                    centerChunk.x + dx,
                    centerChunk.z + dz
                );
                chunksToLoad.add(chunkPos);
            }
        }
        
        // 记录需要强制加载的区块
        Set<net.minecraft.world.level.ChunkPos> forcedChunks = forceLoadedChunks.computeIfAbsent(
            dimKey, k -> ConcurrentHashMap.newKeySet());
        
        int newlyForced = 0;
        int alreadyForced = 0;
        
        for (net.minecraft.world.level.ChunkPos chunkPos : chunksToLoad) {
            // 检查是否已经强制加载
            if (level.getForcedChunks().contains(chunkPos.toLong())) {
                alreadyForced++;
                continue;
            }
            
            // 设置强制加载
            boolean success = level.setChunkForced(chunkPos.x, chunkPos.z, true);
            if (success) {
                forcedChunks.add(chunkPos);
                newlyForced++;
            }
        }
        
        long loadTime = System.currentTimeMillis() - startTime;
        MaidSpellMod.LOGGER.info("强制加载结构区块 - 中心: {}, 范围: {}x{}, 新加载: {}, 已加载: {}, 总计: {}, 耗时: {}ms",
            centerChunk, loadRadius * 2 + 1, loadRadius * 2 + 1, newlyForced, alreadyForced, chunksToLoad.size(), loadTime);
    }
    
    /**
     * 取消结构区块的强制加载（结构生成完成后调用）
     * @param dimKey 维度Key
     * @param structureCenter 结构中心位置
     */
    public static void unforceLoadStructureChunks(ResourceKey<Level> dimKey, BlockPos structureCenter) {
        ServerLevel level = dimensionRegistry.get(dimKey);
        if (level == null) {
            MaidSpellMod.LOGGER.warn("取消强制加载失败：维度未注册 - {}", dimKey);
            return;
        }
        
        Set<net.minecraft.world.level.ChunkPos> forcedChunks = forceLoadedChunks.get(dimKey);
        if (forcedChunks == null || forcedChunks.isEmpty()) {
            return;
        }
        
        net.minecraft.world.level.ChunkPos centerChunk = new net.minecraft.world.level.ChunkPos(structureCenter);
        int loadRadius = 2;
        int unforced = 0;
        
        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dz = -loadRadius; dz <= loadRadius; dz++) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(
                    centerChunk.x + dx,
                    centerChunk.z + dz
                );
                
                if (forcedChunks.remove(chunkPos)) {
                    level.setChunkForced(chunkPos.x, chunkPos.z, false);
                    unforced++;
                }
            }
        }
        
        MaidSpellMod.LOGGER.info("取消强制加载结构区块 - 中心: {}, 取消: {} 个区块", centerChunk, unforced);
    }

    @Nullable
    private static HolderSet<Structure> getOrInitStructureSet(ServerLevel level) {
        if (cachedStructureSet == null) {
            synchronized (STRUCTURE_SET_LOCK) {
                if (cachedStructureSet == null) {
                    try {
                        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                        Holder<Structure> holder = registry.getHolderOrThrow(HIDDEN_RETREAT_KEY);
                        cachedStructureSet = HolderSet.direct(holder);
                    } catch (Exception e) {
                        MaidSpellMod.LOGGER.error("Failed to init structure set", e);
                        return null;
                    }
                }
            }
        }
        return cachedStructureSet;
    }
}
