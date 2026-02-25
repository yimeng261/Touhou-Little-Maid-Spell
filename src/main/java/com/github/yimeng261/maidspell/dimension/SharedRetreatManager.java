package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享归隐之地管理器
 * 负责管理共享维度模式下的结构生成配额
 */
public class SharedRetreatManager {
    
    /** 刚传送进入的玩家，短时间内禁用配额消耗（防止自然生成浪费配额） */
    private static final Set<UUID> recentlyTeleportedPlayers = ConcurrentHashMap.newKeySet();
    
    /**
     * 原子性地尝试消耗一个配额（防止竞态条件）
     * 用于 findGenerationPoint，在结构计划生成时预留配额
     * @param server 服务器实例
     * @return 如果成功消耗配额则返回true
     */
    public static synchronized boolean tryConsumeQuota(MinecraftServer server) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        
        // 找到第一个有配额的玩家并原子性地扣除
        for (RetreatDimensionData.DimensionInfo info : data.getAllDimensions().values()) {
            if (info.structureQuota > 0) {
                info.structureQuota--;
                data.setDirty();
                MaidSpellMod.LOGGER.info("预留玩家 {} 的结构配额，剩余: {}", info.playerUUID, info.structureQuota);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 归还一个配额（结构生成失败时调用）
     * @param server 服务器实例
     */
    public static synchronized void refundQuota(MinecraftServer server) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        
        // 归还给第一个玩家（通常是刚才消耗配额的玩家）
        for (RetreatDimensionData.DimensionInfo info : data.getAllDimensions().values()) {
            info.structureQuota++;
            data.setDirty();
            MaidSpellMod.LOGGER.info("归还玩家 {} 的结构配额，当前: {}", info.playerUUID, info.structureQuota);
            return;
        }
    }
    
    /**
     * 检查特定玩家是否有配额
     * @param server 服务器实例
     * @param playerUUID 玩家UUID
     * @return 如果有配额则返回true
     */
    public static boolean hasQuota(MinecraftServer server, UUID playerUUID) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(playerUUID);
        return info != null && info.structureQuota > 0;
    }
    
}
