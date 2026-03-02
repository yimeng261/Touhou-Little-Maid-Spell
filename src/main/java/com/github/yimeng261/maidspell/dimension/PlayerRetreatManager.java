package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.Config;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.UUID;

/**
 * 玩家归隐之地管理器
 * 负责动态创建和管理玩家专属的归隐之地维度。
 * 维度缓存委托给 RetreatManager。
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class PlayerRetreatManager {

    /**
     * 获取或创建玩家的归隐之地维度
     */
    @SuppressWarnings("removal")
    public static ServerLevel getOrCreatePlayerRetreat(MinecraftServer server, UUID playerUUID) {
        if (!Config.enablePrivateDimensions) {
            return getOrCreateSharedRetreat(server);
        }

        ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);

        // 1. 从 RetreatManager 缓存查找
        ServerLevel cached = RetreatManager.getCachedPlayerRetreat(playerUUID);
        if (cached != null && !cached.isClientSide()) {
            RetreatDimensionData.get(server).updateAccessTime(playerUUID);
            return cached;
        }

        // 2. 从服务器已加载维度查找
        ServerLevel existing = server.getLevel(dimensionKey);
        if (existing != null) {
            RetreatManager.cachePlayerRetreat(playerUUID, existing);
            RetreatManager.registerDimension(dimensionKey, existing);
            RetreatDimensionData.get(server).updateAccessTime(playerUUID);
            return existing;
        }

        // 3. 创建新维度
        return createPlayerRetreat(server, playerUUID);
    }

    @SuppressWarnings("removal")
    private static ServerLevel createPlayerRetreat(MinecraftServer server, UUID playerUUID) {
        try {
            ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);
            ResourceLocation templateDimension = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat");
            boolean success = ((MinecraftServerAccessor) server).maidspell$createWorld(dimensionKey, templateDimension);

            if (success) {
                ServerLevel newLevel = server.getLevel(dimensionKey);
                if (newLevel != null) {
                    RetreatManager.cachePlayerRetreat(playerUUID, newLevel);
                    RetreatManager.registerDimension(dimensionKey, newLevel);

                    RetreatDimensionData data = RetreatDimensionData.get(server);
                    data.registerDimension(playerUUID);

                    MaidSpellMod.LOGGER.info("Created retreat dimension for player: {}", playerUUID);
                    return newLevel;
                }
            }

            MaidSpellMod.LOGGER.error("Failed to create retreat dimension for player: {}", playerUUID);
            return null;
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to create retreat dimension for player: {}", playerUUID, e);
            return null;
        }
    }

    /**
     * 获取或创建共享的归隐之地维度
     */
    @SuppressWarnings("removal")
    public static ServerLevel getOrCreateSharedRetreat(MinecraftServer server) {
        ResourceKey<Level> dimensionKey = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat")
        );

        ServerLevel existingLevel = server.getLevel(dimensionKey);
        if (existingLevel != null) {
            RetreatManager.registerDimension(dimensionKey, existingLevel);
            return existingLevel;
        }

        try {
            ResourceLocation templateDimension = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat");
            boolean success = ((MinecraftServerAccessor) server).maidspell$createWorld(dimensionKey, templateDimension);

            if (success) {
                ServerLevel newLevel = server.getLevel(dimensionKey);
                if (newLevel != null) {
                    RetreatManager.registerDimension(dimensionKey, newLevel);
                    MaidSpellMod.LOGGER.info("Created shared retreat dimension");
                    return newLevel;
                }
            }

            MaidSpellMod.LOGGER.error("Failed to create shared retreat dimension");
            return null;
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to create shared retreat dimension", e);
            return null;
        }
    }

    /**
     * 移除玩家的归隐之地维度（统一清理所有状态）
     */
    public static void removePlayerRetreat(MinecraftServer server, UUID playerUUID) {
        ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);

        RetreatManager.removeCachedPlayerRetreat(playerUUID);
        RetreatManager.unregisterDimension(dimensionKey);

        RetreatDimensionData data = RetreatDimensionData.get(server);
        data.removeDimension(playerUUID);

        ((MinecraftServerAccessor) server).maidspell$removeWorld(dimensionKey);
        MaidSpellMod.LOGGER.info("Removed retreat dimension for player: {}", playerUUID);
    }

    // ========== 服务器事件 ==========

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        RetreatManager.clearPlayerRetreatCache();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        RetreatManager.init();

        RetreatDimensionData data = RetreatDimensionData.get(server);

        if (Config.enablePrivateDimensions) {
            for (UUID playerUUID : data.getAllDimensions().keySet()) {
                ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);
                ServerLevel existingLevel = server.getLevel(dimensionKey);
                if (existingLevel != null) {
                    RetreatManager.cachePlayerRetreat(playerUUID, existingLevel);
                    RetreatManager.registerDimension(dimensionKey, existingLevel);
                    MaidSpellMod.LOGGER.info("Loaded existing retreat dimension for player: {}", playerUUID);
                } else {
                    MaidSpellMod.LOGGER.info("Recreating retreat dimension for player: {}", playerUUID);
                    createPlayerRetreat(server, playerUUID);
                }
            }
            MaidSpellMod.LOGGER.info("Loaded {} retreat dimensions", RetreatManager.getCachedPlayerRetreatCount());
        } else {
            int totalQuota = data.getAllDimensions().values().stream()
                    .mapToInt(info -> info.structureQuota).sum();
            MaidSpellMod.LOGGER.info("Shared retreat mode, total quota: {}", totalQuota);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RetreatManager.shutdown();
    }
}
