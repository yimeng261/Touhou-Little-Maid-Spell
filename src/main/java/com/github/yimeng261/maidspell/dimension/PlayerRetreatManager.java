package com.github.yimeng261.maidspell.dimension;

import com.mojang.serialization.Dynamic;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.accessor.MinecraftServerAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 玩家归隐之地管理器
 * 负责动态创建和管理玩家专属的归隐之地维度。
 * 维度缓存委托给 RetreatManager。
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class PlayerRetreatManager {

    private static final ResourceLocation RETREAT_TEMPLATE = new ResourceLocation(MaidSpellMod.MOD_ID, "the_retreat");
    private static final ResourceKey<Level> SHARED_RETREAT_KEY = ResourceKey.create(Registries.DIMENSION, RETREAT_TEMPLATE);

    /**
     * Valkyrien Skies 对服务器 tick 的阶段有严格约束：在某些阶段插入 addDimension/setPlayers 会直接崩。
     * VS 的 tick 周期是：preTick (setPlayers) → [游戏逻辑] → postTick
     * 我们必须在 VS tick 之前（PHASE_START）创建维度，而不是之后（PHASE_END）
     */
    private static final ConcurrentLinkedQueue<Runnable> START_PHASE_TASKS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<ResourceKey<Level>, CompletableFuture<ServerLevel>> PENDING_CREATIONS =
            new ConcurrentHashMap<>();
    private static volatile boolean startupRestoreComplete = false;

    /**
     * 获取或创建玩家的归隐之地维度
     */
    public static CompletableFuture<ServerLevel> getOrCreatePlayerRetreatAsync(MinecraftServer server, UUID playerUUID) {
        if (!Config.enablePrivateDimensions) {
            return getOrCreateSharedRetreatAsync(server);
        }

        ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);
        return ensureDimensionReady(server, dimensionKey, playerUUID);
    }

    public static CompletableFuture<ServerLevel> getOrCreateSharedRetreatAsync(MinecraftServer server) {
        return ensureDimensionReady(server, SHARED_RETREAT_KEY, null);
    }

    public static CompletableFuture<ServerLevel> getOrCreateRetreatByKeyAsync(MinecraftServer server,
                                                                               ResourceKey<Level> dimensionKey,
                                                                               @Nullable UUID playerUUID) {
        if (!TheRetreatDimension.isRetreatDimension(dimensionKey.location())) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Dimension is not a retreat dimension: " + dimensionKey.location()));
        }
        UUID ownerUUID = SHARED_RETREAT_KEY.equals(dimensionKey) ? null : playerUUID;
        return ensureDimensionReady(server, dimensionKey, ownerUUID);
    }

    @Nullable
    public static ServerLevel getLoadedPlayerRetreat(MinecraftServer server, UUID playerUUID) {
        if (!Config.enablePrivateDimensions) {
            return getLoadedSharedRetreat(server);
        }
        ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);
        return resolveLoadedDimension(server, dimensionKey, playerUUID, true);
    }

    @Nullable
    public static ServerLevel getLoadedSharedRetreat(MinecraftServer server) {
        return resolveLoadedDimension(server, SHARED_RETREAT_KEY, null, false);
    }

    public static ResourceKey<Level> getSharedRetreatKey() {
        return SHARED_RETREAT_KEY;
    }

    @Deprecated
    @Nullable
    public static ServerLevel getOrCreatePlayerRetreat(MinecraftServer server, UUID playerUUID) {
        try {
            return getOrCreatePlayerRetreatAsync(server, playerUUID).getNow(null);
        } catch (CompletionException e) {
            return null;
        }
    }

    @Deprecated
    @Nullable
    public static ServerLevel getOrCreateSharedRetreat(MinecraftServer server) {
        try {
            return getOrCreateSharedRetreatAsync(server).getNow(null);
        } catch (CompletionException e) {
            return null;
        }
    }

    private static CompletableFuture<ServerLevel> ensureDimensionReady(MinecraftServer server,
                                                                       ResourceKey<Level> dimensionKey,
                                                                       @Nullable UUID playerUUID) {
        ServerLevel loaded = resolveLoadedDimension(server, dimensionKey, playerUUID, true);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded);
        }

        CompletableFuture<ServerLevel> pending = PENDING_CREATIONS.get(dimensionKey);
        if (pending != null) {
            return pending;
        }

        CompletableFuture<ServerLevel> creationFuture = new CompletableFuture<>();
        CompletableFuture<ServerLevel> existing = PENDING_CREATIONS.putIfAbsent(dimensionKey, creationFuture);
        if (existing != null) {
            return existing;
        }

        creationFuture.whenComplete((level, throwable) -> PENDING_CREATIONS.remove(dimensionKey, creationFuture));
        enqueueStartPhaseTask(() -> createDimensionOnStartPhase(server, dimensionKey, playerUUID, creationFuture));
        return creationFuture;
    }

    @Nullable
    private static ServerLevel resolveLoadedDimension(MinecraftServer server,
                                                      ResourceKey<Level> dimensionKey,
                                                      @Nullable UUID playerUUID,
                                                      boolean updateAccessTime) {
        if (playerUUID != null) {
            ServerLevel cached = RetreatManager.getCachedPlayerRetreat(playerUUID);
            if (cached != null
                    && cached.getServer() == server
                    && cached.dimension().equals(dimensionKey)
                    && server.getLevel(dimensionKey) == cached) {
                cacheAndRegisterDimension(dimensionKey, cached, playerUUID);
                if (updateAccessTime) {
                    touchPlayerDimension(server, playerUUID);
                }
                return cached;
            }
        }

        ServerLevel existingLevel = server.getLevel(dimensionKey);
        if (existingLevel != null) {
            cacheAndRegisterDimension(dimensionKey, existingLevel, playerUUID);
            if (updateAccessTime) {
                touchPlayerDimension(server, playerUUID);
            }
            return existingLevel;
        }
        return null;
    }

    private static void createDimensionOnStartPhase(MinecraftServer server,
                                                    ResourceKey<Level> dimensionKey,
                                                    @Nullable UUID playerUUID,
                                                    CompletableFuture<ServerLevel> creationFuture) {
        if (creationFuture.isDone()) {
            return;
        }

        try {
            ServerLevel existingLevel = resolveLoadedDimension(server, dimensionKey, playerUUID, true);
            if (existingLevel != null) {
                creationFuture.complete(existingLevel);
                return;
            }

            @SuppressWarnings("null")
            boolean success = ((MinecraftServerAccessor) server).maidspell$createWorld(dimensionKey, RETREAT_TEMPLATE);
            if (!success) {
                IllegalStateException exception =
                        new IllegalStateException("Failed to create retreat dimension: " + dimensionKey.location());
                logCreationFailure(playerUUID, dimensionKey, exception);
                creationFuture.completeExceptionally(exception);
                return;
            }

            ServerLevel newLevel = server.getLevel(dimensionKey);
            if (newLevel == null) {
                IllegalStateException exception =
                        new IllegalStateException("Created retreat dimension but level was unavailable: " + dimensionKey.location());
                logCreationFailure(playerUUID, dimensionKey, exception);
                creationFuture.completeExceptionally(exception);
                return;
            }

            cacheAndRegisterDimension(dimensionKey, newLevel, playerUUID);
            registerPlayerDimension(server, playerUUID);
            logCreationSuccess(playerUUID);
            creationFuture.complete(newLevel);
        } catch (Exception e) {
            logCreationFailure(playerUUID, dimensionKey, e);
            creationFuture.completeExceptionally(e);
        }
    }

    public static synchronized void preloadPersistedRetreatState(MinecraftServer server) {
        if (startupRestoreComplete) {
            return;
        }

        RetreatManager.init();
        RetreatDimensionData data = RetreatDimensionData.get(server);
        Map<ResourceKey<Level>, UUID> playerDataDimensions = scanRetreatDimensionsFromPlayerData(server);
        Map<ResourceKey<Level>, UUID> startupDimensions = new LinkedHashMap<>();

        if (Config.enablePrivateDimensions) {
            for (UUID playerUUID : data.getAllDimensions().keySet()) {
                startupDimensions.putIfAbsent(TheRetreatDimension.getPlayerRetreatDimension(playerUUID), playerUUID);
            }
        }

        playerDataDimensions.forEach(startupDimensions::putIfAbsent);

        int existingDimensions = 0;
        int createdDimensions = 0;
        int failedDimensions = 0;
        for (Map.Entry<ResourceKey<Level>, UUID> entry : startupDimensions.entrySet()) {
            ResourceKey<Level> dimensionKey = entry.getKey();
            UUID ownerUUID = SHARED_RETREAT_KEY.equals(dimensionKey) ? null : entry.getValue();

            if (server.getLevel(dimensionKey) != null) {
                resolveLoadedDimension(server, dimensionKey, ownerUUID, false);
                existingDimensions++;
                continue;
            }

            ServerLevel createdLevel = ensureDimensionReadyImmediately(server, dimensionKey, ownerUUID);
            if (createdLevel != null) {
                createdDimensions++;
            } else {
                failedDimensions++;
            }
        }

        int restoredStructureFlags = 0;
        int restoredSharedCaches = 0;
        for (Map.Entry<UUID, RetreatDimensionData.DimensionInfo> entry : data.getAllDimensions().entrySet()) {
            UUID playerUUID = entry.getKey();
            RetreatDimensionData.DimensionInfo info = entry.getValue();
            ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);

            if (info.structureGenerated) {
                RetreatManager.restoreStructureGenerated(dimensionKey);
                restoredStructureFlags++;
            }
            if (info.foundStructurePos != null) {
                RetreatManager.updateCache(playerUUID, info.foundStructurePos);
                restoredSharedCaches++;
            }
        }

        startupRestoreComplete = true;
        MaidSpellMod.LOGGER.info(
            "Preloaded retreat state during server load: existingDimensions={}, createdDimensions={}, failedDimensions={}, playerDataRetreats={}, restoredStructureFlags={}, restoredSharedCaches={}",
            existingDimensions,
            createdDimensions,
            failedDimensions,
            playerDataDimensions.size(),
            restoredStructureFlags,
            restoredSharedCaches
        );
    }

    private static void enqueueStartPhaseTask(Runnable task) {
        if (task != null) {
            START_PHASE_TASKS.add(task);
        }
    }

    private static void cacheAndRegisterDimension(ResourceKey<Level> dimensionKey,
                                                  ServerLevel level,
                                                  @Nullable UUID playerUUID) {
        if (playerUUID != null) {
            RetreatManager.cachePlayerRetreat(playerUUID, level);
        }
        RetreatManager.registerDimension(dimensionKey, level);
    }

    private static void registerPlayerDimension(MinecraftServer server, @Nullable UUID playerUUID) {
        if (playerUUID != null) {
            RetreatDimensionData.get(server).registerDimension(playerUUID);
        }
    }

    private static void touchPlayerDimension(MinecraftServer server, @Nullable UUID playerUUID) {
        if (playerUUID != null) {
            RetreatDimensionData.get(server).updateAccessTime(playerUUID);
        }
    }

    private static void logCreationSuccess(@Nullable UUID playerUUID) {
        if (playerUUID != null) {
            MaidSpellMod.LOGGER.info("Created retreat dimension for player: {}", playerUUID);
        } else {
            MaidSpellMod.LOGGER.info("Created shared retreat dimension");
        }
    }

    private static void logCreationFailure(@Nullable UUID playerUUID,
                                           ResourceKey<Level> dimensionKey,
                                           Throwable throwable) {
        if (playerUUID != null) {
            MaidSpellMod.LOGGER.error("Failed to create retreat dimension for player: {}", playerUUID, throwable);
        } else {
            MaidSpellMod.LOGGER.error("Failed to create shared retreat dimension: {}", dimensionKey.location(), throwable);
        }
    }

    private static void clearPendingCreations() {
        START_PHASE_TASKS.clear();
        PENDING_CREATIONS.forEach((dimensionKey, future) -> future.completeExceptionally(
                new IllegalStateException("Cleared pending retreat dimension creation: " + dimensionKey.location())));
        PENDING_CREATIONS.clear();
        startupRestoreComplete = false;
    }

    @Nullable
    private static ServerLevel ensureDimensionReadyImmediately(MinecraftServer server,
                                                               ResourceKey<Level> dimensionKey,
                                                               @Nullable UUID playerUUID) {
        ServerLevel loaded = resolveLoadedDimension(server, dimensionKey, playerUUID, false);
        if (loaded != null) {
            return loaded;
        }

        @SuppressWarnings("null")
        boolean success = ((MinecraftServerAccessor) server).maidspell$createWorld(dimensionKey, RETREAT_TEMPLATE);
        if (!success) {
            logCreationFailure(playerUUID, dimensionKey,
                new IllegalStateException("Failed to create retreat dimension during startup: " + dimensionKey.location()));
            return null;
        }

        ServerLevel createdLevel = server.getLevel(dimensionKey);
        if (createdLevel == null) {
            logCreationFailure(playerUUID, dimensionKey,
                new IllegalStateException("Created retreat dimension during startup but level was unavailable: " + dimensionKey.location()));
            return null;
        }

        cacheAndRegisterDimension(dimensionKey, createdLevel, playerUUID);
        registerPlayerDimension(server, playerUUID);
        return createdLevel;
    }

    private static Map<ResourceKey<Level>, UUID> scanRetreatDimensionsFromPlayerData(MinecraftServer server) {
        Map<ResourceKey<Level>, UUID> retreatDimensions = new LinkedHashMap<>();
        File playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        File[] playerFiles = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (playerFiles == null) {
            return retreatDimensions;
        }

        for (File playerFile : playerFiles) {
            UUID playerUUID = parsePlayerUuid(playerFile.getName());
            if (playerUUID == null) {
                continue;
            }

            CompoundTag tag;
            try {
                tag = NbtIo.readCompressed(playerFile);
            } catch (IOException e) {
                MaidSpellMod.LOGGER.warn("Failed to read player data while scanning retreat dimensions: {}", playerFile.getName(), e);
                continue;
            }

            ResourceKey<Level> dimensionKey = parseDimensionKey(tag);
            if (dimensionKey == null || !TheRetreatDimension.isRetreatDimension(dimensionKey.location())) {
                continue;
            }

            UUID ownerUUID = SHARED_RETREAT_KEY.equals(dimensionKey)
                ? null
                : (TheRetreatDimension.getPlayerRetreatDimension(playerUUID).equals(dimensionKey) ? playerUUID : null);
            retreatDimensions.putIfAbsent(dimensionKey, ownerUUID);
        }

        return retreatDimensions;
    }

    @Nullable
    private static UUID parsePlayerUuid(String fileName) {
        String normalized = fileName.endsWith(".dat") ? fileName.substring(0, fileName.length() - 4) : fileName;
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static ResourceKey<Level> parseDimensionKey(CompoundTag tag) {
        if (tag == null || !tag.contains("Dimension")) {
            return null;
        }

        return DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, tag.get("Dimension")))
            .resultOrPartial(MaidSpellMod.LOGGER::error)
            .orElse(null);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 在 VS tick 之前执行（PHASE_START）
        // VS 的 preTick 在 tickServer.HEAD，Forge 的 PHASE_START 在此之前
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        // 每 tick 在 START 阶段尽快清空队列，避免堆积
        Runnable task;
        while ((task = START_PHASE_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                MaidSpellMod.LOGGER.error("Error while running start-phase task", e);
            }
        }
    }

    // ========== 服务器事件 ==========

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        RetreatManager.shutdown();
        clearPendingCreations();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        preloadPersistedRetreatState(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RetreatManager.shutdown();
        clearPendingCreations();
    }
}
