package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 共享归隐之地管理器
 * 负责管理共享维度模式下的结构搜索配额
 */
public class SharedRetreatManager {

    /**
     * 尝试消耗指定玩家的一个配额
     *
     * @param server     服务器实例
     * @param playerUUID 要扣配额的玩家
     * @return 如果成功消耗配额则返回true
     */
    public static synchronized boolean tryConsumeQuota(MinecraftServer server, UUID playerUUID) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(playerUUID);
        if (info != null && info.structureQuota > 0) {
            info.structureQuota--;
            data.setDirty();
            MaidSpellMod.LOGGER.info("消耗玩家 {} 的结构配额，剩余: {}", playerUUID, info.structureQuota);
            return true;
        }
        return false;
    }

    /**
     * 归还指定玩家的一个配额（搜索失败时调用）
     *
     * @param server     服务器实例
     * @param playerUUID 要归还配额的玩家
     */
    public static synchronized void refundQuota(MinecraftServer server, UUID playerUUID) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(playerUUID);
        if (info != null) {
            info.structureQuota++;
            data.setDirty();
            MaidSpellMod.LOGGER.info("归还玩家 {} 的结构配额，当前: {}", playerUUID, info.structureQuota);
        }
    }

    /**
     * 检查特定玩家是否有配额
     *
     * @param server     服务器实例
     * @param playerUUID 玩家UUID
     * @return 如果有配额则返回true
     */
    public static boolean hasQuota(MinecraftServer server, UUID playerUUID) {
        RetreatDimensionData data = RetreatDimensionData.get(server);
        RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(playerUUID);
        return info != null && info.structureQuota > 0;
    }

}
