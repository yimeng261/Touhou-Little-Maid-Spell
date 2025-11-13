package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.accessor.MinecraftServerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家归隐之地管理器
 * 负责动态创建和管理每个玩家专属的归隐之地维度
 *
 * 职责：
 * 1. 维度生命周期管理（创建、删除）
 * 2. 维度缓存管理
 * 3. 维度持久化数据管理
 * 4. 服务器启动/关闭事件处理
 *
 * 实现方式参考：ResourceWorld mod
 * 通过Mixin注入MinecraftServer，实现动态维度创建
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class PlayerRetreatManager {

    // 存储已创建的玩家维度
    private static final Map<UUID, ServerLevel> playerRetreats = new HashMap<>();

    /**
     * 为玩家创建专属的归隐之地维度
     */
    public static ServerLevel createPlayerRetreat(MinecraftServer server, UUID playerUUID) {
        // 检查是否已存在
        if (playerRetreats.containsKey(playerUUID)) {
            return playerRetreats.get(playerUUID);
        }

        try {
            ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);

            // 检查服务器中是否已有此维度
            ServerLevel existingLevel = server.getLevel(dimensionKey);
            if (existingLevel != null) {
                playerRetreats.put(playerUUID, existingLevel);

                // 记录到持久化数据
                RetreatDimensionData data = RetreatDimensionData.get(server);
                data.registerDimension(playerUUID);
                data.updateAccessTime(playerUUID);

                return existingLevel;
            }

            // 使用Mixin accessor创建新维度
            ResourceLocation dimensionType = new ResourceLocation(MaidSpellMod.MOD_ID, "the_retreat");
            boolean success = ((MinecraftServerAccessor) server).maidspell$createWorld(dimensionKey, dimensionType);

            if (success) {
                ServerLevel newLevel = server.getLevel(dimensionKey);
                if (newLevel != null) {
                    playerRetreats.put(playerUUID, newLevel);

                    // 记录到持久化数据
                    RetreatDimensionData data = RetreatDimensionData.get(server);
                    data.registerDimension(playerUUID);

                    MaidSpellMod.LOGGER.info("Successfully created retreat dimension for player: " + playerUUID);
                    return newLevel;
                }
            }

            MaidSpellMod.LOGGER.error("Failed to create retreat dimension for player: " + playerUUID);
            return null;

        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to create retreat dimension for player: " + playerUUID, e);
            return null;
        }
    }

    /**
     * 获取玩家的归隐之地维度（如果不存在则创建）
     */
    public static ServerLevel getOrCreatePlayerRetreat(MinecraftServer server, UUID playerUUID) {
        ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);

        // 先从缓存中查找
        ServerLevel cached = playerRetreats.get(playerUUID);
        if (cached != null && !cached.isClientSide()) {
            // 更新访问时间
            RetreatDimensionData data = RetreatDimensionData.get(server);
            data.updateAccessTime(playerUUID);
            return cached;
        }

        // 从服务器中查找
        ServerLevel existing = server.getLevel(dimensionKey);
        if (existing != null) {
            playerRetreats.put(playerUUID, existing);

            // 更新访问时间
            RetreatDimensionData data = RetreatDimensionData.get(server);
            data.updateAccessTime(playerUUID);

            return existing;
        }

        // 创建新维度
        return createPlayerRetreat(server, playerUUID);
    }

    /**
     * 清理玩家维度缓存
     */
    public static void clearCache() {
        playerRetreats.clear();
        MaidSpellMod.LOGGER.info("Cleared player retreat cache");
    }

    /**
     * 移除玩家的归隐之地维度
     */
    public static void removePlayerRetreat(MinecraftServer server, UUID playerUUID) {
        ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);

        // 从缓存中移除
        playerRetreats.remove(playerUUID);

        // 从持久化数据中移除
        RetreatDimensionData data = RetreatDimensionData.get(server);
        data.removeDimension(playerUUID);

        // 使用Mixin accessor移除维度
        ((MinecraftServerAccessor) server).maidspell$removeWorld(dimensionKey);

        MaidSpellMod.LOGGER.info("Removed retreat dimension for player: " + playerUUID);
    }

    /**
     * 服务器启动时清理缓存
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        clearCache();
    }
}

