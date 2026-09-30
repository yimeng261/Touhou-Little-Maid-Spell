package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.utils.PortableTimerMath;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import javax.annotation.Nullable;
import java.util.Comparator;
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

    // ========== 维度状态 ==========

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
     * 服务器实例 + 维度 + 玩家 UUID → 结构位置缓存
     */
    private static final Map<StructureCacheKey, CacheEntry> structureCache = new ConcurrentHashMap<>();

    /**
     * 搜到结构后加载结构起点周围区块用的票据：只在内存里，60 秒到期自动释放，崩溃或 kill -9 也不会残留。
     */
    private static final TicketType<ChunkPos> STRUCTURE_TICKET = TicketType.create(
            MaidSpellMod.MOD_ID + ":retreat_structure", Comparator.comparingLong(ChunkPos::toLong), 60 * 20);
    private static final int STRUCTURE_TICKET_RADIUS = 2;

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
    private static final long NEGATIVE_CACHE_TTL_TICKS = 5 * 60 * 20L;

    private static class CacheEntry {
        final BlockPos position; // null = 负缓存
        final long timestamp;

        CacheEntry(BlockPos position, long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }

        boolean isExpiredNegative(long currentTime) {
            return position == null
                    && PortableTimerMath.saturatingSubtract(currentTime, timestamp) >= NEGATIVE_CACHE_TTL_TICKS;
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
     * 会话替换或停服时取消进行中的搜索并清空静态状态。
     */
    public static void shutdown() {
        ongoingSearches.values().forEach(StructureSearchWorker::cancel);
        HiddenRetreatStructure.cleanupProcessedStructures("");
        clearAll();
        MaidSpellMod.LOGGER.info("RetreatManager static state cleared");
    }

    private static void clearAll() {
        generatedDimensions.clear();
        playerRetreats.clear();
        structureCache.clear();
        ongoingSearches.clear();
        cachedStructureSet = null;
        searchingCounter.set(0);
        generatedStructurePositions.clear();
    }

    // ========== 维度注销 API ==========

    public static void unregisterDimension(ResourceKey<Level> key) {
        generatedDimensions.remove(key);

        structureCache.keySet().removeIf(cacheKey -> cacheKey.dimension().equals(key));
        playerRetreats.entrySet().removeIf(entry -> entry.getValue().dimension().equals(key));

        // 清理已生成结构位置缓存
        generatedStructurePositions.remove(key);

        // 清理结构去重记录（使用前缀匹配）
        String dimPrefix = key.location() + "@";
        HiddenRetreatStructure.cleanupProcessedStructures(dimPrefix);
        MaidSpellMod.LOGGER.debug("Unregistered dimension: {}", key.location());
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

    /**
     * 原子性标记维度已生成结构，防止重复生成。
     * @return true = 首次标记，允许生成；false = 已被标记，应拦截
     */
    public static boolean tryMarkStructureGenerated(ResourceKey<Level> key) {
        return generatedDimensions.add(key);
    }

    /**
     * [服务器重启恢复] 直接将维度标记为已生成（跳过 pending 阶段）。
     * 仅供 PlayerRetreatManager.onServerStarted() 在恢复持久化数据时使用。
     */
    public static void restoreStructureGenerated(ResourceKey<Level> key) {
        generatedDimensions.add(key);
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

    public static int getCachedPlayerRetreatCount() {
        return playerRetreats.size();
    }

    // ========== 结构缓存 API ==========

    public static CacheResult checkCache(ServerLevel level, UUID playerUUID) {
        StructureCacheKey cacheKey = new StructureCacheKey(level.getServer(), level.dimension(), playerUUID);
        CacheEntry entry = structureCache.get(cacheKey);
        if (entry == null) {
            return CacheResult.NO_CACHE;
        }
        if (entry.isExpiredNegative(level.getServer().overworld().getGameTime())) {
            structureCache.remove(cacheKey, entry);
            return CacheResult.NO_CACHE;
        }
        if (entry.position != null) {
            return CacheResult.found(entry.position);
        }
        return CacheResult.NEGATIVE;
    }

    public static void updateCache(ServerLevel level, UUID playerUUID, @Nullable BlockPos pos) {
        StructureCacheKey cacheKey = new StructureCacheKey(level.getServer(), level.dimension(), playerUUID);
        structureCache.put(cacheKey, new CacheEntry(pos, level.getServer().overworld().getGameTime()));
    }

    public static void clearPlayerCache(UUID playerUUID) {
        structureCache.keySet().removeIf(cacheKey -> cacheKey.playerUUID().equals(playerUUID));
    }

    private record StructureCacheKey(MinecraftServer server, ResourceKey<Level> dimension, UUID playerUUID) {
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

        // 私人维度的结构在生成时记下了位置，从那里搜一圈就能命中；没有记录时从玩家脚下搜
        BlockPos searchCenter = playerPos;
        if (Config.enablePrivateDimensions) {
            UUID owner = PlayerRetreatManager.getPrivateDimensionOwner(level.dimension());
            BlockPos knownPos = owner == null ? null
                    : RetreatDimensionData.get(level.getServer()).getFoundStructurePos(owner);
            if (knownPos != null) {
                searchCenter = knownPos;
            }
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
     * 加载结构起点周围区块，推动结构生成。票据到期自动释放。
     */
    public static void loadStructureChunks(ServerLevel level, BlockPos structureCenter) {
        ChunkPos centerChunk = new ChunkPos(structureCenter);
        level.getChunkSource().addRegionTicket(STRUCTURE_TICKET, centerChunk, STRUCTURE_TICKET_RADIUS, centerChunk);
        MaidSpellMod.LOGGER.info("加载结构区块 - 维度: {}, 中心: {}, 半径: {}",
                level.dimension().location(), centerChunk, STRUCTURE_TICKET_RADIUS);
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
