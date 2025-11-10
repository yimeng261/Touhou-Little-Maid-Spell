package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 搜索缓存管理器（简化版）
 * 在隐世之境中，每个玩家的维度只有一个结构，因此无需基于距离的复杂缓存
 * 只需按维度缓存结构位置即可
 */
public class SearchCacheManager {
    
    // 简化的结构缓存：Key为维度ID，Value为结构位置
    private final Map<String, BlockPos> structureCache = new ConcurrentHashMap<>();
    
    // 正在进行的搜索：避免对同一维度的重复搜索
    private final ConcurrentMap<String, CompletableFuture<BlockPos>> ongoingSearches = new ConcurrentHashMap<>();
    
    /**
     * 缓存检查结果类
     */
    public static class CacheCheckResult {
        public final boolean hasCache;
        public final BlockPos structurePos;
        
        private CacheCheckResult(boolean hasCache, BlockPos pos) {
            this.hasCache = hasCache;
            this.structurePos = pos;
        }
        
        public static CacheCheckResult noCache() {
            return new CacheCheckResult(false, null);
        }
        
        public static CacheCheckResult withResult(BlockPos pos) {
            return new CacheCheckResult(true, pos);
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
        
        // 检查该维度是否已有缓存的结构位置
        BlockPos cachedPos = structureCache.get(dimensionKey);
        if (cachedPos != null) {
            return CacheCheckResult.withResult(cachedPos);
        }
        
        return CacheCheckResult.noCache();
    }
    
    /**
     * 更新缓存
     * @param serverLevel 服务器世界级别
     * @param result 搜索结果（可能为null）
     */
    public void updateCache(ServerLevel serverLevel, BlockPos result) {
        String dimensionKey = serverLevel.dimension().location().toString();
        // 只有在找到结构时才缓存，未找到结构时不缓存以便后续重试
        if (result != null) {
            structureCache.put(dimensionKey, result);
        }
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
