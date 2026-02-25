package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 共享归隐之地管理器
 * 负责管理共享维度模式下的结构生成配额
 */
public class SharedRetreatManager {
    
    /**
     * 检查是否有可用的配额用于生成结构
     * @param server 服务器实例
     * @return 如果有可用配额则返回true
     */
    public static boolean hasAvailableQuota(MinecraftServer server) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        
        // 检查是否有任何玩家的配额>0
        return data.getAllDimensions().values().stream()
            .anyMatch(info -> info.structureQuota > 0);
    }
    
    /**
     * 消耗一个配额（结构生成时调用）
     * @param server 服务器实例
     * @return 如果成功消耗配额则返回true
     */
    public static boolean consumeQuota(MinecraftServer server) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        
        // 找到第一个有配额的玩家并扣除
        for (RetreatDimensionData.DimensionInfo info : data.getAllDimensions().values()) {
            if (info.structureQuota > 0) {
                info.structureQuota--;
                data.setDirty();
                MaidSpellMod.LOGGER.info("消耗玩家 {} 的结构配额，剩余: {}", info.playerUUID, info.structureQuota);
                return true;
            }
        }
        
        return false;
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
