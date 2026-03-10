package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 结构搜索队列
 * 存储等待隐世之境结构生成的玩家搜索请求
 * 
 * 工作机制：
 * - 先到先出（FIFO）原则：每个结构生成只通知队列中第一个玩家
 * - 私人模式：每个玩家有独立维度（不同worldSeed），队列按维度自然分离
 * - 共享模式：所有玩家共享维度（相同worldSeed），按使用寻风之铃的顺序通知
 * 
 * 示例：
 * 玩家A使用寻风之铃 → 加入队列[A]
 * 玩家B使用寻风之铃 → 加入队列[A, B]
 * 结构1生成 → 通知A，队列变为[B]
 * 结构2生成 → 通知B，队列变为[]
 */
public class StructureSearchQueue {
    
    /**
     * 搜索请求（包含回调Future）
     */
    public static class SearchRequest {
        public final UUID playerUUID;
        public final BlockPos searchPosition;  // 玩家搜索时的位置
        public final long timestamp;
        public final CompletableFuture<BlockPos> resultFuture;  // 结果回调
        
        public SearchRequest(UUID playerUUID, BlockPos searchPosition) {
            this.playerUUID = playerUUID;
            this.searchPosition = searchPosition;
            this.timestamp = System.currentTimeMillis();
            this.resultFuture = new CompletableFuture<>();
        }
        
        /**
         * 计算到目标位置的距离平方
         */
        public double distanceSquaredTo(BlockPos target) {
            return searchPosition.distSqr(target);
        }
        
        /**
         * 完成搜索请求，返回结果
         */
        public void complete(BlockPos result) {
            if (!resultFuture.isDone()) {
                resultFuture.complete(result);
            }
        }
        
        /**
         * 取消搜索请求
         */
        public void cancel() {
            if (!resultFuture.isDone()) {
                resultFuture.cancel(true);
            }
        }
    }
    
    // 共享维度的搜索队列（按维度种子分组）
    private static final Map<Long, Queue<SearchRequest>> searchQueues = new ConcurrentHashMap<>();
    
    /**
     * 添加搜索请求到队列，返回结果Future
     * 如果玩家已有请求，则取消旧请求并创建新请求
     * @param worldSeed 维度种子
     * @param playerUUID 玩家UUID
     * @param position 玩家位置
     * @return 结果Future，当结构生成完成时会被complete
     */
    public static CompletableFuture<BlockPos> addSearchRequest(long worldSeed, UUID playerUUID, BlockPos position) {
        Queue<SearchRequest> queue = searchQueues.computeIfAbsent(worldSeed, k -> new ConcurrentLinkedQueue<>());
        
        // 移除该玩家的旧请求（如果存在）并取消
        boolean wasInQueue = false;
        Iterator<SearchRequest> iterator = queue.iterator();
        while (iterator.hasNext()) {
            SearchRequest req = iterator.next();
            if (req.playerUUID.equals(playerUUID)) {
                req.cancel();
                iterator.remove();
                wasInQueue = true;
                break;
            }
        }
        
        // 添加新请求
        SearchRequest newRequest = new SearchRequest(playerUUID, position);
        queue.add(newRequest);
        
        int queueSize = queue.size();
        if (wasInQueue) {
            MaidSpellMod.LOGGER.info("更新结构搜索请求 - 玩家: {}, 位置: {}, 队列位置: {}/{}", 
                playerUUID, position, queueSize, queueSize);
        } else {
            MaidSpellMod.LOGGER.info("添加结构搜索请求 - 玩家: {}, 位置: {}, 队列位置: {}/{}", 
                playerUUID, position, queueSize, queueSize);
        }
        
        return newRequest.resultFuture;
    }
    
    /**
     * 移除特定玩家的搜索请求
     * @param worldSeed 维度种子
     * @param playerUUID 玩家UUID
     */
    public static void removeSearchRequest(long worldSeed, UUID playerUUID) {
        Queue<SearchRequest> queue = searchQueues.get(worldSeed);
        if (queue != null) {
            queue.removeIf(req -> req.playerUUID.equals(playerUUID));
        }
    }
    
    /**
     * 结构生成时调用：先到先出原则，只通知队列中第一个玩家
     * 通过complete Future的方式通知
     * @param worldSeed 维度种子
     * @param structurePos 结构位置（null表示搜索失败）
     * @return 被通知的玩家UUID，如果没有则返回null
     */
    public static UUID notifyNextPlayer(long worldSeed, BlockPos structurePos) {
        Queue<SearchRequest> queue = searchQueues.get(worldSeed);
        if (queue == null || queue.isEmpty()) {
            MaidSpellMod.LOGGER.debug("没有等待结构生成的玩家");
            return null;
        }
        
        // 先到先出：从队列头部取出第一个请求
        SearchRequest request = queue.poll();
        if (request == null) {
            return null;
        }
        
        if (structurePos != null) {
            double distanceSq = request.distanceSquaredTo(structurePos);
            MaidSpellMod.LOGGER.info("完成搜索请求 - 玩家: {}, 结构位置: {}, 距离: {} 方块 (FIFO)", 
                request.playerUUID, structurePos, (int)Math.sqrt(distanceSq));
        } else {
            MaidSpellMod.LOGGER.info("搜索失败通知 - 玩家: {} (FIFO)", request.playerUUID);
        }
        
        // 完成Future，通知等待的搜索任务
        request.complete(structurePos);
        
        return request.playerUUID;
    }

    /**
     * 检查是否有等待的搜索请求
     * @param worldSeed 维度种子
     * @return 如果有等待请求则返回true
     */
    public static boolean hasWaitingRequests(long worldSeed) {
        Queue<SearchRequest> queue = searchQueues.get(worldSeed);
        return queue != null && !queue.isEmpty();
    }
    
    /**
     * 获取等待请求数量
     * @param worldSeed 维度种子
     * @return 等待请求数量
     */
    public static int getWaitingCount(long worldSeed) {
        Queue<SearchRequest> queue = searchQueues.get(worldSeed);
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * 获取玩家在队列中的位置
     * @param worldSeed 维度种子
     * @param playerUUID 玩家UUID
     * @return 队列位置（1-based），如果不在队列中则返回-1
     */
    public static int getPlayerPosition(long worldSeed, UUID playerUUID) {
        Queue<SearchRequest> queue = searchQueues.get(worldSeed);
        if (queue == null || queue.isEmpty()) {
            return -1;
        }
        
        int position = 1;
        for (SearchRequest request : queue) {
            if (request.playerUUID.equals(playerUUID)) {
                return position;
            }
            position++;
        }
        
        return -1;
    }
    
    /**
     * 从所有队列中移除指定玩家的请求（玩家断线时调用）
     * @param playerUUID 玩家UUID
     */
    public static void removePlayerFromAllQueues(UUID playerUUID) {
        for (Queue<SearchRequest> queue : searchQueues.values()) {
            queue.removeIf(req -> {
                if (req.playerUUID.equals(playerUUID)) {
                    req.cancel();
                    MaidSpellMod.LOGGER.debug("玩家断线，移除搜索请求 - 玩家: {}", playerUUID);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * 清理超时的搜索请求（超过5分钟）
     * 取消对应的Future，避免内存泄漏
     */
    public static void cleanupExpiredRequests() {
        long currentTime = System.currentTimeMillis();
        long timeout = 5 * 60 * 1000; // 5分钟
        
        for (Queue<SearchRequest> queue : searchQueues.values()) {
            queue.removeIf(request -> {
                boolean expired = (currentTime - request.timestamp) > timeout;
                if (expired) {
                    request.cancel();  // 取消Future
                    MaidSpellMod.LOGGER.debug("清理超时搜索请求 - 玩家: {}", request.playerUUID);
                }
                return expired;
            });
        }
    }
    
    /**
     * 清空所有队列，取消所有待处理的Future
     */
    public static void clearAll() {
        int totalCancelled = 0;
        for (Queue<SearchRequest> queue : searchQueues.values()) {
            for (SearchRequest request : queue) {
                request.cancel();
                totalCancelled++;
            }
        }
        searchQueues.clear();
        MaidSpellMod.LOGGER.info("清空所有结构搜索队列，取消了 {} 个待处理请求", totalCancelled);
    }
}
