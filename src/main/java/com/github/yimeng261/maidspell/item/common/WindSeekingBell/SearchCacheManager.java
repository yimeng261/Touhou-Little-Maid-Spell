package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 搜索缓存管理器（简化版）
 * 在隐世之境中，每个玩家的维度只有一个结构，因此无需基于距离的复杂缓存
 * 只需按维度缓存结构位置即可
 * 
 * 优化：支持负缓存（未找到结构），避免反复触发昂贵搜索
 */
public class SearchCacheManager {
    
    // 结构缓存：Key为维度ID，Value为Optional（empty表示未找到结构）
    private final Map<String, CacheEntry> structureCache = new ConcurrentHashMap<>();
    
    // 正在进行的搜索：避免对同一维度的重复搜索
    private final ConcurrentMap<String, CompletableFuture<BlockPos>> ongoingSearches = new ConcurrentHashMap<>();
    
    // 负缓存过期时间（毫秒），5分钟后允许重新搜索
    private static final long NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000;
    
    /**
     * 缓存条目，支持正缓存和负缓存
     */
    private static class CacheEntry {
        final BlockPos position; // null 表示未找到（负缓存）
        final long timestamp;
        
        CacheEntry(BlockPos position) {
            this.position = position;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isNegativeCache() {
            return position == null;
        }
        
        boolean isNegativeCacheExpired() {
            return isNegativeCache() && 
                   (System.currentTimeMillis() - timestamp) > NEGATIVE_CACHE_TTL_MS;
        }
    }
    
    /**
     * 缓存检查结果类
     */
    public static class CacheCheckResult {
        public final boolean hasCache;
        public final boolean isNegativeCache; // 是否是负缓存（已搜索但未找到）
        public final BlockPos structurePos;
        
        private CacheCheckResult(boolean hasCache, boolean isNegativeCache, BlockPos pos) {
            this.hasCache = hasCache;
            this.isNegativeCache = isNegativeCache;
            this.structurePos = pos;
        }
        
        public static CacheCheckResult noCache() {
            return new CacheCheckResult(false, false, null);
        }
        
        public static CacheCheckResult withResult(BlockPos pos) {
            return new CacheCheckResult(true, false, pos);
        }
        
        public static CacheCheckResult withNegativeCache() {
            return new CacheCheckResult(true, true, null);
        }
    }
    
    /**
     * 检查缓存中是否有该维度的搜索结果
     * @param serverLevel 服务器世界级别
     * @return 缓存检查结果
     */
    public CacheCheckResult checkCache(ServerLevel serverLevel) {
        // 获取维度标识符
        String dimensionKey = serverLevel.dimension().location().toString();
        
        // 检查该维度是否已有缓存
        CacheEntry entry = structureCache.get(dimensionKey);
        if (entry != null) {
            // 如果是负缓存且已过期，则移除并返回无缓存
            if (entry.isNegativeCacheExpired()) {
                structureCache.remove(dimensionKey);
                return CacheCheckResult.noCache();
            }
            
            // 如果是正缓存（找到了结构）
            if (!entry.isNegativeCache()) {
                return CacheCheckResult.withResult(entry.position);
            }
            
            // 负缓存未过期，返回"已缓存但未找到"
            return CacheCheckResult.withNegativeCache();
        }
        
        return CacheCheckResult.noCache();
    }
    
    /**
     * 更新缓存
     * @param serverLevel 服务器世界级别
     * @param result 搜索结果（可能为null，null会被缓存为负缓存）
     */
    public void updateCache(ServerLevel serverLevel, BlockPos result) {
        String dimensionKey = serverLevel.dimension().location().toString();
        // 无论是否找到结构都缓存
        // null 会被缓存为负缓存，带有TTL，过期后会重新搜索
        structureCache.put(dimensionKey, new CacheEntry(result));
    }

    /**
     * 生成基于维度的搜索键
     * @param serverLevel 服务器世界
     * @return 维度级别的搜索键
     */
    public String generateDimensionKey(ServerLevel serverLevel) {
        return serverLevel.dimension().location().toString();
    }
    
    /**
     * 获取正在进行的搜索
     * @param searchKey 搜索键
     * @return 正在进行的搜索任务，如果不存在则返回null
     */
    public CompletableFuture<BlockPos> getOngoingSearch(String searchKey) {
        return ongoingSearches.get(searchKey);
    }
    
    /**
     * 注册一个新的搜索任务
     * @param searchKey 搜索键
     * @param searchFuture 搜索Future
     */
    public void registerSearch(String searchKey, CompletableFuture<BlockPos> searchFuture) {
        ongoingSearches.put(searchKey, searchFuture);
    }
    
    /**
     * 移除一个已完成的搜索任务
     * @param searchKey 搜索键
     */
    public void removeSearch(String searchKey) {
        ongoingSearches.remove(searchKey);
    }
    
    /**
     * 清空所有缓存数据
     */
    public void clearAll() {
        structureCache.clear();
        ongoingSearches.clear();
    }
}
