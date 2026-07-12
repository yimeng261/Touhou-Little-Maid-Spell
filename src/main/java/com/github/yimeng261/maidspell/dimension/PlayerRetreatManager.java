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
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 玩家归隐之地管理器
 * 负责动态创建和管理玩家专属的归隐之地维度。
 * 维度缓存委托给 RetreatManager。
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class PlayerRetreatManager {

    private static final ResourceLocation RETREAT_TEMPLATE = new ResourceLocation(MaidSpellMod.MOD_ID, "the_retreat");
    private static final ResourceKey<Level> SHARED_RETREAT_KEY = ResourceKey.create(Registries.DIMENSION, RETREAT_TEMPLATE);
    private static final String PRIVATE_RETREAT_PREFIX = "the_retreat_";

    /**
     * Valkyrien Skies 对服务器 tick 的阶段有严格约束：在某些阶段插入 addDimension/setPlayers 会直接崩。
     * VS 的 tick 周期是：preTick (setPlayers) → [游戏逻辑] → postTick
     * 我们必须在 VS tick 之前（PHASE_START）创建维度，而不是之后（PHASE_END）
     */
    private static final ConcurrentLinkedQueue<StartPhaseTask> START_PHASE_TASKS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<ResourceKey<Level>, PendingCreation> PENDING_CREATIONS =
            new ConcurrentHashMap<>();
    private static final AtomicLong SESSION_EPOCH = new AtomicLong();
    private static volatile MinecraftServer activeServer;
    private static volatile boolean acceptingRequests;
    private static volatile boolean startupRestoreComplete = false;

    private record StartPhaseTask(MinecraftServer server, long epoch, Runnable action) {
    }

    private record PendingCreation(MinecraftServer server, long epoch,
                                   CompletableFuture<ServerLevel> future) {
    }

    private record PlayerDataRetreatReferences(Map<ResourceKey<Level>, UUID> dimensions,
                                               Set<UUID> players) {
    }

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
        UUID ownerUUID = resolvePlayerDimensionOwner(dimensionKey, playerUUID);
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
        long epoch = SESSION_EPOCH.get();
        if (!isActiveSession(server, epoch)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Retreat dimension requests are not accepted for this server session"));
        }

        ServerLevel loaded = resolveLoadedDimension(server, dimensionKey, playerUUID, true);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded);
        }

        PendingCreation pending = PENDING_CREATIONS.get(dimensionKey);
        if (pending != null && pending.server() == server && pending.epoch() == epoch) {
            return pending.future();
        }
        if (pending != null && PENDING_CREATIONS.remove(dimensionKey, pending)) {
            pending.future().completeExceptionally(new IllegalStateException(
                    "Discarded a stale retreat dimension creation request: " + dimensionKey.location()));
        }

        CompletableFuture<ServerLevel> creationFuture = new CompletableFuture<>();
        PendingCreation creation = new PendingCreation(server, epoch, creationFuture);
        PendingCreation existing = PENDING_CREATIONS.putIfAbsent(dimensionKey, creation);
        if (existing != null) {
            if (existing.server() == server && existing.epoch() == epoch) {
                return existing.future();
            }
            creationFuture.completeExceptionally(new IllegalStateException(
                    "A stale retreat dimension creation request is still being cleared"));
            return creationFuture;
        }

        creationFuture.whenComplete((level, throwable) -> PENDING_CREATIONS.remove(dimensionKey, creation));
        if (!enqueueStartPhaseTask(server, epoch,
                () -> createDimensionOnStartPhase(dimensionKey, playerUUID, creation))) {
            creationFuture.completeExceptionally(new IllegalStateException(
                    "Server session ended before retreat dimension creation was queued"));
        }
        return creationFuture;
    }

    @Nullable
    private static ServerLevel resolveLoadedDimension(MinecraftServer server,
                                                      ResourceKey<Level> dimensionKey,
                                                      @Nullable UUID playerUUID,
                                                      boolean updateAccessTime) {
        UUID ownerUUID = resolvePlayerDimensionOwner(dimensionKey, playerUUID);
        if (ownerUUID != null) {
            ServerLevel cached = RetreatManager.getCachedPlayerRetreat(ownerUUID);
            if (cached != null
                    && cached.getServer() == server
                    && cached.dimension().equals(dimensionKey)
                    && server.getLevel(dimensionKey) == cached) {
                cacheAndRegisterDimension(dimensionKey, cached, ownerUUID);
                if (updateAccessTime) {
                    touchPlayerDimension(server, ownerUUID);
                }
                return cached;
            }
        }

        ServerLevel existingLevel = server.getLevel(dimensionKey);
        if (existingLevel != null) {
            cacheAndRegisterDimension(dimensionKey, existingLevel, ownerUUID);
            if (updateAccessTime) {
                touchPlayerDimension(server, ownerUUID);
            }
            return existingLevel;
        }
        return null;
    }

    private static void createDimensionOnStartPhase(ResourceKey<Level> dimensionKey,
                                                    @Nullable UUID playerUUID,
                                                    PendingCreation creation) {
        MinecraftServer server = creation.server();
        CompletableFuture<ServerLevel> creationFuture = creation.future();
        if (creationFuture.isDone()) {
            return;
        }
        if (!isActiveSession(server, creation.epoch())
                || PENDING_CREATIONS.get(dimensionKey) != creation) {
            creationFuture.completeExceptionally(new IllegalStateException(
                    "Retreat dimension creation belongs to an inactive server session"));
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

            if (!isActiveSession(server, creation.epoch())
                    || PENDING_CREATIONS.get(dimensionKey) != creation) {
                creationFuture.completeExceptionally(new IllegalStateException(
                        "Server session ended while creating retreat dimension: " + dimensionKey.location()));
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
            UUID ownerUUID = resolvePlayerDimensionOwner(dimensionKey, playerUUID);
            registerPlayerDimension(server, ownerUUID);
            logCreationSuccess(ownerUUID);
            creationFuture.complete(newLevel);
        } catch (Exception e) {
            logCreationFailure(playerUUID, dimensionKey, e);
            creationFuture.completeExceptionally(e);
        }
    }

    public static synchronized void preloadPersistedRetreatState(MinecraftServer server) {
        long epoch = SESSION_EPOCH.get();
        if (!isActiveSession(server, epoch) || startupRestoreComplete) {
            return;
        }

        RetreatManager.init();
        RetreatDimensionData data = RetreatDimensionData.get(server);
        PlayerDataRetreatReferences playerDataReferences = scanRetreatDimensionsFromPlayerData(server);
        Map<ResourceKey<Level>, UUID> startupDimensions = playerDataReferences.dimensions();

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

            ServerLevel createdLevel = ensureDimensionReadyImmediately(server, epoch, dimensionKey, ownerUUID);
            if (createdLevel != null) {
                createdDimensions++;
            } else {
                failedDimensions++;
            }
        }

        Set<UUID> protectedPlayers = new HashSet<>(playerDataReferences.players());
        startupDimensions.values().forEach(ownerUUID -> {
            if (ownerUUID != null) {
                protectedPlayers.add(ownerUUID);
            }
        });
        server.getPlayerList().getPlayers().forEach(player -> protectedPlayers.add(player.getUUID()));
        int cleanedMetadata = 0;
        if (Config.retreatRecordRetentionDays > 0) {
            long retentionMillis = TimeUnit.DAYS.toMillis(Config.retreatRecordRetentionDays);
            cleanedMetadata = data.cleanupOldDimensions(retentionMillis, protectedPlayers);
        }

        if (!isActiveSession(server, epoch)) {
            return;
        }
        startupRestoreComplete = true;
        MaidSpellMod.LOGGER.info(
            "Preloaded referenced retreat levels during server load: existingDimensions={}, createdDimensions={}, failedDimensions={}, playerDataRetreats={}, protectedPlayers={}, cleanedMetadata={}",
            existingDimensions,
            createdDimensions,
            failedDimensions,
            startupDimensions.size(),
            protectedPlayers.size(),
            cleanedMetadata
        );
    }

    private static boolean enqueueStartPhaseTask(MinecraftServer server, long epoch, Runnable task) {
        if (task == null || !isActiveSession(server, epoch)) {
            return false;
        }
        START_PHASE_TASKS.add(new StartPhaseTask(server, epoch, task));
        return true;
    }

    private static void cacheAndRegisterDimension(ResourceKey<Level> dimensionKey,
                                                  ServerLevel level,
                                                  @Nullable UUID playerUUID) {
        UUID ownerUUID = resolvePlayerDimensionOwner(dimensionKey, playerUUID);
        RetreatManager.registerDimension(dimensionKey, level);
        if (ownerUUID != null) {
            RetreatManager.cachePlayerRetreat(ownerUUID, level);
            RetreatDimensionData.DimensionInfo info = RetreatDimensionData.get(level.getServer())
                .getDimensionInfo(ownerUUID);
            if (info != null && info.structureGenerated) {
                RetreatManager.restoreStructureGenerated(dimensionKey);
            }
        }
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

    private static void clearPendingCreations(String reason) {
        START_PHASE_TASKS.clear();
        PENDING_CREATIONS.forEach((dimensionKey, creation) -> creation.future().completeExceptionally(
                new IllegalStateException(reason + ": " + dimensionKey.location())));
        PENDING_CREATIONS.clear();
        startupRestoreComplete = false;
    }

    @Nullable
    private static ServerLevel ensureDimensionReadyImmediately(MinecraftServer server,
                                                               long epoch,
                                                               ResourceKey<Level> dimensionKey,
                                                               @Nullable UUID playerUUID) {
        if (!isActiveSession(server, epoch)) {
            return null;
        }
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

        if (!isActiveSession(server, epoch)) {
            return null;
        }

        ServerLevel createdLevel = server.getLevel(dimensionKey);
        if (createdLevel == null) {
            logCreationFailure(playerUUID, dimensionKey,
                new IllegalStateException("Created retreat dimension during startup but level was unavailable: " + dimensionKey.location()));
            return null;
        }

        cacheAndRegisterDimension(dimensionKey, createdLevel, playerUUID);
        registerPlayerDimension(server, resolvePlayerDimensionOwner(dimensionKey, playerUUID));
        return createdLevel;
    }

    private static boolean isActiveSession(MinecraftServer server, long epoch) {
        return server != null
                && activeServer == server
                && acceptingRequests
                && SESSION_EPOCH.get() == epoch;
    }

    private static PlayerDataRetreatReferences scanRetreatDimensionsFromPlayerData(MinecraftServer server) {
        Map<ResourceKey<Level>, UUID> retreatDimensions = new LinkedHashMap<>();
        Set<UUID> referencedPlayers = new HashSet<>();
        File playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        File[] playerFiles = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (playerFiles == null) {
            return new PlayerDataRetreatReferences(retreatDimensions, referencedPlayers);
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

            referencedPlayers.add(playerUUID);
            UUID ownerUUID = resolvePlayerDimensionOwner(dimensionKey, playerUUID);
            retreatDimensions.putIfAbsent(dimensionKey, ownerUUID);
        }

        return new PlayerDataRetreatReferences(retreatDimensions, referencedPlayers);
    }

    @Nullable
    private static UUID resolvePlayerDimensionOwner(ResourceKey<Level> dimensionKey,
                                                    @Nullable UUID fallbackPlayerUUID) {
        if (dimensionKey == null || SHARED_RETREAT_KEY.equals(dimensionKey)) {
            return null;
        }

        ResourceLocation location = dimensionKey.location();
        if (MaidSpellMod.MOD_ID.equals(location.getNamespace())
                && location.getPath().startsWith(PRIVATE_RETREAT_PREFIX)) {
            String encodedUuid = location.getPath().substring(PRIVATE_RETREAT_PREFIX.length());
            try {
                UUID parsed = UUID.fromString(encodedUuid.replace('_', '-'));
                if (TheRetreatDimension.getPlayerRetreatDimension(parsed).equals(dimensionKey)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to the validated caller-provided owner.
            }
        }

        if (fallbackPlayerUUID != null
                && TheRetreatDimension.getPlayerRetreatDimension(fallbackPlayerUUID).equals(dimensionKey)) {
            return fallbackPlayerUUID;
        }
        return null;
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

        MinecraftServer server = event.getServer();
        long epoch = SESSION_EPOCH.get();
        if (!isActiveSession(server, epoch)) {
            return;
        }

        // 每 tick 在 START 阶段尽快清空队列，避免堆积
        StartPhaseTask task;
        while ((task = START_PHASE_TASKS.poll()) != null) {
            if (task.server() != server || task.epoch() != epoch || !isActiveSession(server, epoch)) {
                continue;
            }
            try {
                task.action().run();
            } catch (Exception e) {
                MaidSpellMod.LOGGER.error("Error while running start-phase task", e);
            }
        }
    }

    // ========== 服务器事件 ==========

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        acceptingRequests = false;
        SESSION_EPOCH.incrementAndGet();
        clearPendingCreations("Server session was replaced before retreat dimension creation completed");
        RetreatManager.shutdown();
        activeServer = event.getServer();
        acceptingRequests = true;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (isActiveSession(server, SESSION_EPOCH.get())) {
            preloadPersistedRetreatState(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (activeServer != server) {
            return;
        }

        acceptingRequests = false;
        SESSION_EPOCH.incrementAndGet();
        clearPendingCreations("Server stopped before retreat dimension creation completed");
        RetreatManager.shutdown(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (activeServer != event.getServer()) {
            return;
        }

        acceptingRequests = false;
        SESSION_EPOCH.incrementAndGet();
        clearPendingCreations("Server session ended before retreat dimension creation completed");
        RetreatManager.shutdown();
        activeServer = null;
    }
}
