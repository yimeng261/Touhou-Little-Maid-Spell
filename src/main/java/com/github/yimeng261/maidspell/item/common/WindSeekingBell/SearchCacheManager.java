package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 搜索缓存管理器
 * 负责管理结构搜索的缓存和正在进行的搜索任务
 */
public class SearchCacheManager {
    
    // 结构缓存：Key为"x,z"字符串，Value为CacheEntry
    private final Map<String, CacheEntry> structureCache = new ConcurrentHashMap<>();
    
    // 正在进行的搜索：避免对同一区域的重复搜索
    private final ConcurrentMap<String, CompletableFuture<BlockPos>> ongoingSearches = new ConcurrentHashMap<>();
    
    /**
     * 缓存条目类
     */
    private static class CacheEntry {
        final BlockPos searchCenter;  // 搜索中心位置
        final BlockPos structurePos;  // 结构位置，null表示该区域没有结构
        final long timestamp;         // 缓存时间戳
        final String dimensionKey;    // 维度标识符，确保不跨维度使用缓存
        
        CacheEntry(BlockPos searchCenter, BlockPos structurePos, long time, String dimensionKey) {
            this.searchCenter = searchCenter;
            this.structurePos = structurePos;
            this.timestamp = time;
            this.dimensionKey = dimensionKey;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > SearchConfig.CACHE_EXPIRY_TIME;
        }
        
        /**
         * 检查当前玩家位置是否可以使用此缓存
         * @param playerPos 当前玩家位置
         * @param currentDimensionKey 当前维度标识符
         * @return 是否可以使用缓存
         */
        boolean isValidForPosition(BlockPos playerPos, String currentDimensionKey) {
            // 检查维度是否匹配
            if (!this.dimensionKey.equals(currentDimensionKey)) {
                return false;
            }
            
            double moveDistance = Math.sqrt(playerPos.distSqr(searchCenter));
            return moveDistance <= SearchConfig.CACHE_MOVE_THRESHOLD;
        }
    }
    
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
     * 检查缓存中是否有该位置的搜索结果
     * @param serverLevel 服务器世界级别
     * @param playerPos 玩家位置
     * @return 缓存检查结果
     */
    public CacheCheckResult checkCache(ServerLevel serverLevel, BlockPos playerPos) {
        // 清理过期缓存
        cleanExpiredCache();
        
        // 获取当前维度标识符
        String currentDimensionKey = serverLevel.dimension().location().toString();
        
        // 检查是否有可用的缓存结果
        for (CacheEntry cache : structureCache.values()) {
            if (cache.isExpired()) continue;
            
            // 检查玩家相对于原搜索位置的移动距离和维度匹配
            if (cache.isValidForPosition(playerPos, currentDimensionKey)) {
                return CacheCheckResult.withResult(cache.structurePos); // 可能是null，表示该区域没有结构
            }
        }
        
        return CacheCheckResult.noCache(); // 没有找到相关缓存，需要进行搜索
    }
    
    /**
     * 更新缓存
     * @param serverLevel 服务器世界级别
     * @param searchCenter 搜索中心位置
     * @param result 搜索结果（可能为null）
     */
    public void updateCache(ServerLevel serverLevel, BlockPos searchCenter, BlockPos result) {
        String key = generateSearchKey(searchCenter);
        String dimensionKey = serverLevel.dimension().location().toString();
        CacheEntry entry = new CacheEntry(searchCenter, result, System.currentTimeMillis(), dimensionKey);
        structureCache.put(key, entry);
    }
    
    /**
     * 清理过期缓存
     */
    public void cleanExpiredCache() {
        structureCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 生成区域性搜索键
     * 将相近位置的搜索归为同一区域，避免重复搜索
     * 使用更智能的区域判定算法
     * @param pos 玩家位置
     * @return 区域性搜索键
     */
    public String generateSearchKey(BlockPos pos) {
        // 检查是否已有进行中的搜索可以复用
        for (Map.Entry<String, CompletableFuture<BlockPos>> entry : ongoingSearches.entrySet()) {
            String[] coords = entry.getKey().split(",");
            try {
                int existingX = Integer.parseInt(coords[0]);
                int existingZ = Integer.parseInt(coords[1]);
                BlockPos existingCenter = new BlockPos(existingX, 0, existingZ);
                
                // 如果距离现有搜索中心在区域范围内，使用现有的key
                double distance = Math.sqrt(pos.distSqr(existingCenter));
                if (distance <= SearchConfig.SEARCH_REGION_SIZE) {
                    return entry.getKey();
                }
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        
        // 如果没有可复用的搜索，创建新的区域键
        // 使用当前位置作为新区域的中心
        return pos.getX() + "," + pos.getZ();
    }
    
    /**
     * 获取或注册正在进行的搜索
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
