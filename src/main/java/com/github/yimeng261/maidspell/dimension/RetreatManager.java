package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 归隐之地统一状态管理器。
 * 管理维度缓存、结构生成标记、结构位置缓存和分帧搜索。
 */
public class RetreatManager {

    // ========== 维度注册 ==========

    /**
     * 维度 ResourceKey → ServerLevel 映射
     */
    private static final Map<ResourceKey<Level>, ServerLevel> dimensionRegistry = new ConcurrentHashMap<>();

    /**
     * 已生成隐世之境结构的维度集合
     */
    private static final Set<ResourceKey<Level>> generatedDimensions = ConcurrentHashMap.newKeySet();

    /**
     * 玩家 UUID → ServerLevel 缓存
     */
    private static final Map<UUID, ServerLevel> playerRetreats = new ConcurrentHashMap<>();

    // ========== 结构缓存 ==========

    /**
     * 玩家 UUID → 结构位置缓存
     */
    private static final Map<UUID, CacheEntry> structureCache = new ConcurrentHashMap<>();

    /**
     * 强制加载的结构区块：维度 ResourceKey → 区块位置集合
     */
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> forceLoadedChunks = new ConcurrentHashMap<>();

    /**
     * 结构搜索信号量（计数器）。
     * SearchWorker 开始搜索时 +1，generate() 成功后 -1。
     * generate() 通过检查 counter > 0 判断当前是否有搜索在进行。
     * 使用 AtomicInteger 保证在 C2ME 等并发区块生成下的线程安全。
     */
    private static final AtomicInteger searchingCounter = new AtomicInteger(0);

    /**
     * generate() 成功后存储的结构位置（维度 → 位置）。
     * 让 SearchWorker 在下一次 doWork() 时立即得知结构已生成并短路搜索。
     */
    private static final ConcurrentHashMap<ResourceKey<Level>, BlockPos> generatedStructurePositions = new ConcurrentHashMap<>();

    /**
     * 负缓存 TTL：5 分钟
     */
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

    // ========== 搜索状态 ==========

    /**
     * 正在进行的搜索，防止重复搜索
     */
    private static final Map<String, StructureSearchWorker> ongoingSearches = new ConcurrentHashMap<>();

    /**
     * 结构 HolderSet 缓存（延迟初始化）
     */
    private static volatile HolderSet<Structure> cachedStructureSet = null;
    private static final Object STRUCTURE_SET_LOCK = new Object();

    private static final ResourceKey<Structure> HIDDEN_RETREAT_KEY =
            ResourceKey.create(Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "hidden_retreat"));

    private static final ResourceKey<StructureSet> HIDDEN_RETREAT_SET_KEY =
            ResourceKey.create(Registries.STRUCTURE_SET,
                    ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "hidden_retreat_set"));

    // ========== 生命周期 ==========

    /**
     * 服务器启动时调用
     */
    public static void init() {
        clearAll();
        MaidSpellMod.LOGGER.info("RetreatManager initialized");
    }

    /**
     * 服务器关闭时调用
     */
    public static void shutdown() {
        // 取消所有进行中的搜索
        ongoingSearches.values().forEach(StructureSearchWorker::cancel);
        // 清理结构去重记录
        HiddenRetreatStructure.cleanupProcessedStructures("");
        clearAll();
        MaidSpellMod.LOGGER.info("RetreatManager shutdown");
    }

    private static void clearAll() {
        dimensionRegistry.clear();
        generatedDimensions.clear();
        playerRetreats.clear();
        structureCache.clear();
        ongoingSearches.clear();
        forceLoadedChunks.clear();
        cachedStructureSet = null;
        searchingCounter.set(0);
        generatedStructurePositions.clear();
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
        Set<ChunkPos> forcedChunks = forceLoadedChunks.remove(key);
        if (forcedChunks != null && level != null) {
            for (ChunkPos chunkPos : forcedChunks) {
                level.setChunkForced(chunkPos.x, chunkPos.z, false);
            }
            MaidSpellMod.LOGGER.info("清理维度强制加载区块 - 维度: {}, 数量: {}", key.location(), forcedChunks.size());
        }

        // 清理已生成结构位置缓存
        generatedStructurePositions.remove(key);

        // 清理结构去重记录（使用前缀匹配）
        String dimPrefix = key.location() + "@";
        HiddenRetreatStructure.cleanupProcessedStructures(dimPrefix);
        MaidSpellMod.LOGGER.debug("Unregistered dimension: {}", key.location());
    }

    public static boolean isDimensionRegistered(ResourceKey<Level> key) {
        return dimensionRegistry.containsKey(key);
    }

    @Nullable
    public static ServerLevel getDimensionLevel(ResourceKey<Level> key) {
        return dimensionRegistry.get(key);
    }

    /**
     * 通过 seed 查找维度（兼容 generate() 中只有 seed 的场景）
     */
    @Nullable
    public static ResourceKey<Level> findDimensionKeyBySeed(long seed) {
        for (Map.Entry<ResourceKey<Level>, ServerLevel> entry : dimensionRegistry.entrySet()) {
            if (entry.getValue().getSeed() == seed) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ========== 搜索信号量 ==========

    /**
     * 搜索开始，信号量 +1。
     * 由 SearchWorker.start() 调用。
     */
    public static void incrementSearching() {
        searchingCounter.incrementAndGet();
    }

    /**
     * 检查当前是否有搜索正在进行（信号量 > 0）。
     * 用于 canCreateStructure 路径判断是否应该缓存结果。
     */
    public static boolean isSearchActive() {
        return searchingCounter.get() > 0;
    }

    /**
     * CAS 获取一个搜索许可（信号量 -1）。
     * <p>
     * 由 generate() 在执行前调用：只有成功获取许可的 generate() 才允许执行，
     * 保证并发 generate() 不会同时通过。
     *
     * @return true 获取成功（counter 从 N>0 减到 N-1），false 没有可用许可
     */
    public static boolean tryAcquireSearchPermit() {
        int prev;
        do {
            prev = searchingCounter.get();
            if (prev <= 0) {
                return false;
            }
        } while (!searchingCounter.compareAndSet(prev, prev - 1));
        return true;
    }

    /**
     * 归还一个搜索许可（信号量 +1）。
     * <p>
     * generate() 获取许可后但生成失败（findGenerationPoint 返回空）时调用，
     * 归还许可以允许后续候选区块重试。
     */
    public static void releaseSearchPermit() {
        searchingCounter.incrementAndGet();
    }

    // ========== generate() 成功回调 ==========

    /**
     * generate() 成功生成结构后调用。
     * 存储结构位置，让 SearchWorker 在下一次 doWork() 时立即感知。
     * <p>
     * 注意：此方法不操作信号量——许可已在 {@link #tryAcquireSearchPermit()} 中扣减。
     */
    public static void setGeneratedStructurePos(ResourceKey<Level> dimKey, BlockPos pos) {
        generatedStructurePositions.put(dimKey, pos);
    }

    /**
     * SearchWorker 轮询：获取并移除 generate() 存储的结构位置。
     *
     * @return 结构位置，如果没有则返回 null
     */
    @Nullable
    public static BlockPos pollGeneratedStructurePos(ResourceKey<Level> dimKey) {
        return generatedStructurePositions.remove(dimKey);
    }

    /**
     * 非破坏性查看：检查是否有 generate() 存储的结构位置，但不移除。
     * 用于在 checkCandidate 中判断信号量状态，而不消费位置。
     *
     * @return 结构位置，如果没有则返回 null
     */
    @Nullable
    public static BlockPos peekGeneratedStructurePos(ResourceKey<Level> dimKey) {
        return generatedStructurePositions.get(dimKey);
    }

    // ========== 结构生成标记 ==========

    public static boolean isStructureGenerated(ResourceKey<Level> key) {
        return generatedDimensions.contains(key);
    }

    /**
     * 原子性标记维度已生成结构，防止重复生成。
     * @return true = 首次标记，允许生成；false = 已被标记，应拦截
     */
    public static boolean tryMarkStructureGenerated(ResourceKey<Level> key) {
        return generatedDimensions.add(key);
    }

    /**
     * 回退结构生成标记（当 generate() 失败时调用）。
     */
    public static void unmarkStructureGenerated(ResourceKey<Level> key) {
        generatedDimensions.remove(key);
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

    /**
     * 缓存查询结果
     */
    public static class CacheResult {
        public static final CacheResult NO_CACHE = new CacheResult(false, false, null);
        public static final CacheResult NEGATIVE = new CacheResult(true, true, null);

        public final boolean hasCache;
        public final boolean isNegative;
        @Nullable
        public final BlockPos position;

        private CacheResult(boolean hasCache, boolean isNegative, @Nullable BlockPos position) {
            this.hasCache = hasCache;
            this.isNegative = isNegative;
            this.position = position;
        }

        public static CacheResult found(BlockPos pos) {
            return new CacheResult(true, false, pos);
        }
    }

    // ========== 搜索 API ==========

    /**
     * 搜索隐世之境结构，返回结果 Future。
     * 使用 StructureSearchWorker 在主线程分帧执行，不阻塞也无线程安全问题。
     */
    public static CompletableFuture<BlockPos> searchStructure(ServerLevel level, UUID playerUUID, BlockPos playerPos) {
        String searchKey = makeSearchKey(level, playerUUID);

        // 已有搜索在进行，返回已有 Future
        StructureSearchWorker existingSearch = ongoingSearches.get(searchKey);
        if (existingSearch != null) {
            return existingSearch.getResultFuture();
        }

        // 获取结构 Holder 和 StructurePlacement
        HolderSet<Structure> structureSet = getOrInitStructureSet(level);
        if (structureSet == null) {
            MaidSpellMod.LOGGER.error("Failed to init structure set for search");
            return CompletableFuture.completedFuture(null);
        }

        RandomSpreadStructurePlacement placement = getStructurePlacement(level);
        if (placement == null) {
            MaidSpellMod.LOGGER.error("Failed to get RandomSpreadStructurePlacement for hidden_retreat_set");
            return CompletableFuture.completedFuture(null);
        }

        // 确定搜索中心
        BlockPos searchCenter;
        if (Config.enablePrivateDimensions) {
            ResourceKey<Level> dimResourceKey = level.dimension();
            if (generatedDimensions.contains(dimResourceKey)) {
                searchCenter = level.getSharedSpawnPos();
            } else {
                searchCenter = playerPos;
            }
        } else {
            searchCenter = playerPos;
        }

        // 共享模式跳过已知结构
        boolean skipKnown = !Config.enablePrivateDimensions;

        // 创建分帧搜索器
        Set<Holder<Structure>> holderSet = new HashSet<>();
        for (Holder<Structure> h : structureSet) {
            holderSet.add(h);
        }

        StructureSearchWorker search = new StructureSearchWorker(
                level, holderSet, placement, searchCenter, 100, skipKnown
        );
        ongoingSearches.put(searchKey, search);

        // 搜索完成后清理
        search.getResultFuture().whenComplete((result, throwable) -> {
            ongoingSearches.remove(searchKey);
            if (throwable != null && !(throwable instanceof CancellationException)) {
                MaidSpellMod.LOGGER.error("结构搜索异常 - key: {}", searchKey, throwable);
            }
        });

        // 启动搜索（注册到 WorldWorkerManager）
        search.start();

        return search.getResultFuture();
    }

    /**
     * 构造搜索 key（私人模式用维度 key，共享模式用 "dimKey:playerUUID"）
     */
    private static String makeSearchKey(ServerLevel level, UUID playerUUID) {
        String dimKey = level.dimension().location().toString();
        if (Config.enablePrivateDimensions) {
            return dimKey;
        } else {
            return dimKey + ":" + playerUUID;
        }
    }

    /**
     * 获取隐世之境结构的 RandomSpreadStructurePlacement
     */
    @Nullable
    private static RandomSpreadStructurePlacement getStructurePlacement(ServerLevel level) {
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
            Holder<StructureSet> setHolder = registry.getHolderOrThrow(HIDDEN_RETREAT_SET_KEY);
            StructurePlacement placement = setHolder.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement rsp) {
                return rsp;
            }
            MaidSpellMod.LOGGER.error("hidden_retreat_set placement is not RandomSpreadStructurePlacement: {}",
                    placement.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to get structure placement for hidden_retreat_set", e);
            return null;
        }
    }

    /**
     * 强制加载结构所在区块，触发结构生成。
     */
    public static void forceLoadStructureChunks(ServerLevel level, BlockPos structureCenter) {
        ChunkPos centerChunk = new ChunkPos(structureCenter);
        ResourceKey<Level> dimKey = level.dimension();

        int loadRadius = 2;
        Set<ChunkPos> forcedChunks = forceLoadedChunks.computeIfAbsent(
                dimKey, k -> ConcurrentHashMap.newKeySet());

        int newlyForced = 0;
        int alreadyForced = 0;

        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dz = -loadRadius; dz <= loadRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);

                if (level.getForcedChunks().contains(chunkPos.toLong())) {
                    alreadyForced++;
                    continue;
                }

                boolean success = level.setChunkForced(chunkPos.x, chunkPos.z, true);
                if (success) {
                    forcedChunks.add(chunkPos);
                    newlyForced++;
                }
            }
        }

        MaidSpellMod.LOGGER.info("强制加载结构区块 - 中心: {}, 范围: {}x{}, 新加载: {}, 已加载: {}, 总计: {}",
                centerChunk, loadRadius * 2 + 1, loadRadius * 2 + 1, newlyForced, alreadyForced,
                (loadRadius * 2 + 1) * (loadRadius * 2 + 1));
    }

    /**
     * 取消结构区块的强制加载
     */
    public static void unforceLoadStructureChunks(ResourceKey<Level> dimKey, BlockPos structureCenter) {
        ServerLevel level = dimensionRegistry.get(dimKey);
        if (level == null) {
            MaidSpellMod.LOGGER.warn("取消强制加载失败：维度未注册 - {}", dimKey);
            return;
        }

        Set<ChunkPos> forcedChunks = forceLoadedChunks.get(dimKey);
        if (forcedChunks == null || forcedChunks.isEmpty()) {
            return;
        }

        ChunkPos centerChunk = new ChunkPos(structureCenter);
        int loadRadius = 2;
        int unforced = 0;

        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dz = -loadRadius; dz <= loadRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(
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
        HolderSet<Structure> localRef = cachedStructureSet;
        if (localRef == null) {
            synchronized (STRUCTURE_SET_LOCK) {
                localRef = cachedStructureSet;
                if (localRef == null) {
                    try {
                        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                        Holder<Structure> holder = registry.getHolderOrThrow(HIDDEN_RETREAT_KEY);
                        localRef = HolderSet.direct(holder);
                        cachedStructureSet = localRef;
                    } catch (Exception e) {
                        MaidSpellMod.LOGGER.error("Failed to init structure set", e);
                        return null;
                    }
                }
            }
        }
        return localRef;
    }
}
