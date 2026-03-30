package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.accessor.MinecraftServerAccessor;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 玩家归隐之地管理器
 * 负责动态创建和管理玩家专属的归隐之地维度。
 * 维度缓存委托给 RetreatManager。
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class PlayerRetreatManager {

    private static final ResourceLocation RETREAT_TEMPLATE = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat");
    private static final ResourceKey<Level> SHARED_RETREAT_KEY = ResourceKey.create(Registries.DIMENSION, RETREAT_TEMPLATE);

    /**
     * Valkyrien Skies 对服务器 tick 的阶段有严格约束：在某些阶段插入 addDimension/setPlayers 会直接崩。
     * VS 的 tick 周期是：preTick (setPlayers) → [游戏逻辑] → postTick
     * 我们必须在 VS tick 之前（Pre）创建维度，而不是之后（Post）
     */
    private static final ConcurrentLinkedQueue<Runnable> START_PHASE_TASKS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<ResourceKey<Level>, CompletableFuture<ServerLevel>> PENDING_CREATIONS =
            new ConcurrentHashMap<>();

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
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
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
        MinecraftServer server = event.getServer();
        RetreatManager.init();

        RetreatDimensionData data = RetreatDimensionData.get(server);

        if (Config.enablePrivateDimensions) {
            AtomicInteger restoredStructureFlags = new AtomicInteger();
            int loadedDimensions = 0;
            int queuedRecreations = 0;
            for (var entry : data.getAllDimensions().entrySet()) {
                UUID playerUUID = entry.getKey();
                RetreatDimensionData.DimensionInfo info = entry.getValue();
                ResourceKey<Level> dimensionKey = TheRetreatDimension.getPlayerRetreatDimension(playerUUID);
                ServerLevel existingLevel = getLoadedPlayerRetreat(server, playerUUID);
                if (existingLevel != null) {
                    loadedDimensions++;
                    // 恢复结构已生成标记，防止重启后重复生成
                    if (info.structureGenerated) {
                        RetreatManager.restoreStructureGenerated(dimensionKey);
                        restoredStructureFlags.incrementAndGet();
                    }
                    MaidSpellMod.LOGGER.info("Loaded existing retreat dimension for player: {}", playerUUID);
                } else {
                    queuedRecreations++;
                    MaidSpellMod.LOGGER.info("Recreating retreat dimension for player: {}", playerUUID);
                    CompletableFuture<ServerLevel> future = getOrCreatePlayerRetreatAsync(server, playerUUID);
                    if (info.structureGenerated) {
                        future.thenAccept(level -> {
                            RetreatManager.restoreStructureGenerated(dimensionKey);
                            restoredStructureFlags.incrementAndGet();
                        }).exceptionally(throwable -> {
                            MaidSpellMod.LOGGER.error("Failed to restore retreat dimension for player: {}", playerUUID, throwable);
                            return null;
                        });
                    }
                }
            }
            MaidSpellMod.LOGGER.info(
                    "Loaded {} retreat dimensions, queued {} recreations, restored {} structure generated flags",
                    loadedDimensions, queuedRecreations, restoredStructureFlags.get());
        } else {
            int totalQuota = data.getAllDimensions().values().stream()
                    .mapToInt(info -> info.structureQuota).sum();
            // 恢复持久化的结构位置到内存缓存
            int restoredCount = 0;
            for (var entry : data.getAllDimensions().entrySet()) {
                if (entry.getValue().foundStructurePos != null) {
                    RetreatManager.updateCache(entry.getKey(), entry.getValue().foundStructurePos);
                    restoredCount++;
                }
            }
            MaidSpellMod.LOGGER.info("Shared retreat mode, total quota: {}, restored {} cached structure positions",
                    totalQuota, restoredCount);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RetreatManager.shutdown();
        clearPendingCreations();
    }
}
