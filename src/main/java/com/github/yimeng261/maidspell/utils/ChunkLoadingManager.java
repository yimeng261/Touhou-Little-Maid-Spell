package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 区块加载管理器
 * 管理装备锚定核心的女仆的区块强加载
 * 支持数据持久化，确保服务器重启后能恢复区块加载状态
 * ChunkKey 只保存维度键，运行时票据按服务器会话和 ServerLevel 实例隔离。
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class ChunkLoadingManager {
    private static final int TRANSIENT_TICKET_TIMEOUT_TICKS = 100;
    private static final TicketType<UUID> PROVISIONAL_TICKET_TYPE = TicketType.create(
            "maidspell_anchor_restore", (first, second) -> first.compareTo(second),
            TRANSIENT_TICKET_TIMEOUT_TICKS);
    private static final TicketType<UUID> TELEPORT_PRELOAD_TICKET_TYPE = TicketType.create(
            "maidspell_teleport_preload", (first, second) -> first.compareTo(second),
            TRANSIENT_TICKET_TIMEOUT_TICKS);
    private static final int PROVISIONAL_TICKET_DISTANCE = 2;
    private static final boolean PROVISIONAL_TICKET_FORCE_TICKS = false;
    private static final int PROVISIONAL_TICKET_RENEW_INTERVAL_TICKS = 20;
    private static final int TELEPORT_PRELOAD_TICKET_DISTANCE = 2;
    private static final boolean TELEPORT_PRELOAD_FORCE_TICKS = false;
    private static final AtomicLong SERVER_EPOCH = new AtomicLong();
    private static volatile MinecraftServer activeServer;
    private static volatile boolean acceptingRequests;

    // 存储每个女仆的区块加载状态，包含维度信息
    private static final Map<UUID, Set<ChunkKey>> maidChunkPositions = new ConcurrentHashMap<>();
    
    private static final Map<ChunkKey, ChunkAnchorState> activeChunkAnchors = new ConcurrentHashMap<>();

    private static final Map<PendingKey, RestoreRequest> restoreRequests = new ConcurrentHashMap<>();
    private static final Map<PendingKey, UUID> requestTokens = new ConcurrentHashMap<>();
    private static final Map<PendingKey, ProvisionalTicket> provisionalTickets = new ConcurrentHashMap<>();
    private static final Map<PendingKey, Long> provisionalReleaseRetries = new ConcurrentHashMap<>();
    private static final Map<PendingKey, TeleportPreloadTicket> teleportPreloads = new ConcurrentHashMap<>();
    private static final Map<PendingKey, PendingLevelRequest> pendingLevelRequests = new ConcurrentHashMap<>();
    private static final Map<PendingKey, Long> pendingLevelDeadlines = new ConcurrentHashMap<>();
    private static final Queue<CompletedLevelRequest> completedLevelRequests = new ConcurrentLinkedQueue<>();
    private static final Map<PendingKey, RecoveryRetry> recoveryRetries = new ConcurrentHashMap<>();
    private static final Map<PendingKey, FormalReleaseRetry> formalReleaseRetries = new ConcurrentHashMap<>();
    private static final Map<PendingKey, ActiveForcedTicket> activeForcedTickets = new ConcurrentHashMap<>();
    private static final Map<StaleForcedTicketKey, StaleForcedTicketRelease> staleForcedTicketReleases =
            new ConcurrentHashMap<>();

    private static final int CHECK_INTERVAL_TICKS = 20; // 每20tick检查一次
    private static final int TELEPORT_PRELOAD_LIFETIME_TICKS = TRANSIENT_TICKET_TIMEOUT_TICKS;
    private static final int RESTORE_RETRY_INTERVAL_TICKS = 20;
    private static final int RESTORE_RETRY_ATTEMPTS = 10;
    private static final int RESTORE_OBSERVATION_TIMEOUT_TICKS = CHECK_INTERVAL_TICKS * 30;
    private static final int LEVEL_REQUEST_TIMEOUT_TICKS = CHECK_INTERVAL_TICKS * 30;
    private static final int MAX_LEVEL_COMPLETIONS_PER_TICK = 8;
    private static final int MAX_LEVEL_COMPLETION_SCANS_PER_TICK = 128;
    private static final int RECOVERY_RETRY_INTERVAL_TICKS = CHECK_INTERVAL_TICKS * 30;
    private static final int TICKET_RELEASE_RETRY_INTERVAL_TICKS = CHECK_INTERVAL_TICKS;

    private record PendingKey(UUID maidId, ChunkKey chunkKey) {
    }

    private record RestoreRequest(long epoch, MinecraftServer server, ServerLevel level, UUID token,
                                  int attemptsRemaining, long retryAtTick,
                                  long waitDeadlineTick, AnchorRestorePolicy.RestorePhase phase) {
    }

    private record ProvisionalTicket(long epoch, MinecraftServer server, ServerLevel level, UUID token,
                                     long nextRenewTick) {
    }

    private record TeleportPreloadTicket(long epoch, MinecraftServer server, ServerLevel level, UUID token,
                                         long expiresAtTick, long nextReleaseAttemptTick) {
    }

    private record PendingLevelRequest(long epoch, MinecraftServer server, UUID token,
                                       CompletableFuture<ServerLevel> future) {
    }

    private record CompletedLevelRequest(PendingKey pendingKey, PendingLevelRequest request,
                                         ServerLevel level, Throwable throwable) {
    }

    private record RecoveryRetry(long epoch, MinecraftServer server, long retryAtTick) {
    }

    private record FormalReleaseRetry(long epoch, MinecraftServer server, long retryAtTick) {
    }

    private record ActiveForcedTicket(long epoch, MinecraftServer server, ServerLevel level) {
    }

    private record StaleForcedTicketKey(PendingKey pendingKey, ActiveForcedTicket ticket) {
    }

    private record StaleForcedTicketRelease(long retryAtTick, int attemptsRemaining) {
    }

    /**
     * 区块键类，用于唯一标识一个区块
     * 使用 ResourceKey<Level> 代替 ServerLevel，避免可变对象作为Map键
     */
    public static final class ChunkKey {
        private final ChunkPos chunkPos;
        private final ResourceKey<Level> dimension;
        
        public ChunkKey(ChunkPos chunkPos, ResourceKey<Level> dimension) {
            this.chunkPos = chunkPos;
            this.dimension = dimension;
        }
        
        /**
         * 便捷构造方法：从 ServerLevel 创建
         */
        public ChunkKey(ChunkPos chunkPos, ServerLevel level) {
            this(chunkPos, level.dimension());
        }
        
        public ChunkPos chunkPos() {
            return chunkPos;
        }
        
        public ResourceKey<Level> dimension() {
            return dimension;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkKey other)) return false;
            return chunkPos.equals(other.chunkPos) && dimension.equals(other.dimension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos, dimension);
        }

        @Override
        public String toString() {
            return chunkPos + " (" + dimension.location() + ")";
        }
    }
    
    /** 当前由 Anchor Core 正式票据关联到同一区块的女仆集合。 */
    private static class ChunkAnchorState {
        private final Set<UUID> associatedMaids;
        private final ChunkKey chunkKey;

        private ChunkAnchorState(ChunkKey chunkKey) {
            this.chunkKey = chunkKey;
            this.associatedMaids = ConcurrentHashMap.newKeySet();
        }
        
        public void addMaid(UUID maidId) {
            associatedMaids.add(maidId);
        }

        public void removeMaid(UUID maidId) {
            associatedMaids.remove(maidId);
        }

        public boolean hasMaid(UUID maidId) {
            return associatedMaids.contains(maidId);
        }

        public boolean hasNoMaids() {
            return associatedMaids.isEmpty();
        }
        
        public Set<UUID> getAssociatedMaids() {
            return new HashSet<>(associatedMaids);
        }

        @Override
        public String toString() {
            return String.format("ChunkAnchorState{%s, maids=%s}", chunkKey, associatedMaids);
        }
    }
    
    /** 为装备 Anchor Core 的女仆启用区块加载。 */
    public static void enableChunkLoading(EntityMaid maid) {
        if (maid == null || !(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        UUID maidId = maid.getUUID();
        ChunkKey chunkKey = new ChunkKey(maid.chunkPosition(), serverLevel);
        executeOnServer(server, () -> {
            if (!isActiveSession(server) || maid.isRemoved() || !maid.isAlive()
                    || maid.level() != serverLevel || server.getLevel(serverLevel.dimension()) != serverLevel
                    || !maid.chunkPosition().equals(chunkKey.chunkPos())
                    || !BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                return;
            }
            performChunkOperationOnServer(server, maidId, chunkKey, true);
        });
    }

    /**
     * 立即释放指定女仆持有的所有强加载票据。
     * 例如 Carry On 搬起实体时会用 UNLOADED_WITH_PLAYER 让实体暂时离开世界；
     * 这类正常离开不应继续保留锚定核心的旧区块强加载。
     */
    public static void disableChunkLoading(EntityMaid maid) {
        if (maid == null || !(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        UUID maidId = maid.getUUID();
        executeOnServer(server, () -> {
            if (activeServer != server) {
                return;
            }

            cancelMaidRequests(maidId);
            Set<ChunkKey> chunks = new HashSet<>();
            Set<ChunkKey> persistedChunks = maidChunkPositions.remove(maidId);
            if (persistedChunks != null) {
                chunks.addAll(persistedChunks);
            }
            activeForcedTickets.keySet().stream()
                    .filter(key -> key.maidId().equals(maidId))
                    .map(PendingKey::chunkKey)
                    .forEach(chunks::add);
            formalReleaseRetries.keySet().stream()
                    .filter(key -> key.maidId().equals(maidId))
                    .map(PendingKey::chunkKey)
                    .forEach(chunks::add);
            activeChunkAnchors.forEach((chunkKey, state) -> {
                if (state.hasMaid(maidId)) {
                    chunks.add(chunkKey);
                }
            });

            for (ChunkKey chunkKey : chunks) {
                performChunkOperationOnServer(server, maidId, chunkKey, false);
            }
        });
    }

    
    /**
     * 预加载传送目标区块
     * @param maid 女仆实体
     * @param targetPos 目标位置
     * @param level 目标维度
     */
    public static void preloadTeleportTarget(EntityMaid maid, Vec3 targetPos, ServerLevel level) {
        if (maid == null || targetPos == null || level == null) {
            return;
        }
        
        UUID maidId = maid.getUUID();
        ChunkPos targetChunk = new ChunkPos(BlockPos.containing(targetPos));
        ChunkKey targetKey = new ChunkKey(targetChunk, level);
        
        MinecraftServer server = level.getServer();
        executeOnServer(server, () -> {
            if (!isActiveSession(server)
                    || server.getLevel(level.dimension()) != level
                    || maid.isRemoved() || !maid.isAlive()
                    || !BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                return;
            }
            acquireTeleportPreload(new PendingKey(maidId, targetKey), server, level);
        });
    }
    
    /** 处理短票据、恢复请求和正式 Anchor 关联。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!isActiveSession(server)) {
            return;
        }
        renewProvisionalTickets(server);
        processProvisionalReleaseRetries(server);
        processTeleportPreloads(server);
        processFormalReleaseRetries(server);
        processStaleForcedTicketReleases(server);
        processCompletedLevelRequests(server);
        processPendingLevelRequestTimeouts(server);
        processRestoreRequests(server);
        processRecoveryRetries(server);
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            validateActiveChunkAnchors(server);
        }
    }

    private static void validateActiveChunkAnchors(MinecraftServer server) {
        if (activeChunkAnchors.isEmpty()) {
            return;
        }

        for (Map.Entry<ChunkKey, ChunkAnchorState> entry : activeChunkAnchors.entrySet()) {
            ChunkKey chunkKey = entry.getKey();
            ChunkAnchorState state = entry.getValue();
            ServerLevel level = server.getLevel(chunkKey.dimension());
            if (level == null) {
                if (activeChunkAnchors.remove(chunkKey, state)) {
                    for (UUID maidId : state.getAssociatedMaids()) {
                        PendingKey pendingKey = new PendingKey(maidId, chunkKey);
                        ActiveForcedTicket staleTicket = activeForcedTickets.remove(pendingKey);
                        if (staleTicket != null) {
                            scheduleStaleForcedTicketRelease(pendingKey, staleTicket);
                        }
                        formalReleaseRetries.remove(pendingKey);
                        scheduleRecoveryRetry(pendingKey, server,
                                "dimension became temporarily unavailable");
                    }
                }
                continue;
            }

            for (UUID maidId : state.getAssociatedMaids()) {
                PendingKey pendingKey = new PendingKey(maidId, chunkKey);
                if (!isForcedTicketActive(pendingKey, server, level)) {
                    state.removeMaid(maidId);
                    formalReleaseRetries.remove(pendingKey);
                    scheduleRecoveryRetry(pendingKey, server,
                            "server level changed while the formal ticket was active");
                }
            }
            if (state.hasNoMaids()) {
                activeChunkAnchors.remove(chunkKey, state);
                continue;
            }
            if (!level.areEntitiesLoaded(chunkKey.chunkPos().toLong())) {
                continue;
            }

            for (UUID maidId : state.getAssociatedMaids()) {
                try {
                    boolean isValid = level.getEntity(maidId) instanceof EntityMaid maid
                            && !maid.isRemoved() && maid.isAlive()
                            && BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)
                            && maid.chunkPosition().equals(chunkKey.chunkPos());
                    if (!isValid) {
                        state.removeMaid(maidId);
                        performChunkOperationOnServer(server, maidId, chunkKey, false);
                    }
                } catch (Exception e) {
                    Global.LOGGER.warn("校验女仆 {} 的 Anchor Core 区块状态失败，将在下次检查重试",
                            maidId, e);
                }
            }

            if (state.hasNoMaids()) {
                activeChunkAnchors.remove(chunkKey, state);
            }
        }
    }

    /**
     * 获取当前服务器实例
     */
    public static MinecraftServer getCurrentServer() {
        return activeServer;
    }

    // ===== 私有辅助方法 =====
    private static boolean performChunkOperationOnServer(MinecraftServer server, UUID maidId, ChunkKey info,
                                                         boolean enable) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Chunk operations must run on the server thread");
        }
        if (activeServer != server || (enable && !acceptingRequests)) {
            return false;
        }

        PendingKey pendingKey = new PendingKey(maidId, info);
        ServerLevel serverLevel = server.getLevel(info.dimension());
        if (!enable) {
            cancelRequestState(pendingKey);
            removeChunkTracking(server, pendingKey);
            return true;
        }
        if (serverLevel == null) {
            return false;
        }

        ChunkPos chunkPos = info.chunkPos();
        if (!isChunkLoaded(serverLevel, chunkPos)) {
            UUID token = requestTokens.computeIfAbsent(pendingKey, key -> UUID.randomUUID());
            if (!acquireProvisionalTicket(pendingKey, server, serverLevel, token)) {
                abortTransientRequest(pendingKey, server, token,
                        "failed to acquire provisional entity ticket");
                return false;
            }
            scheduleRestoreRequest(pendingKey, server, serverLevel, token,
                    RESTORE_RETRY_ATTEMPTS, AnchorRestorePolicy.RestorePhase.WAITING_CHUNK);
            return true;
        }

        try {
            commitForcedChunkTracking(server, serverLevel, pendingKey);
            cancelRequestState(pendingKey);
            return true;
        } catch (Exception e) {
            Global.LOGGER.error("启用 Anchor Core 区块强加载失败: {}", info, e);
            return false;
        }
    }

    private static void scheduleRestoreRequest(PendingKey pendingKey, MinecraftServer server,
                                               ServerLevel level, UUID token, int attemptsRemaining,
                                               AnchorRestorePolicy.RestorePhase phase) {
        if (!ensureRequestTicketCurrent(pendingKey, server, level, token,
                "restore scheduling lost its provisional ticket")) {
            return;
        }

        long currentTick = server.getTickCount();
        RestoreRequest request = new RestoreRequest(
                SERVER_EPOCH.get(), server, level, token,
                attemptsRemaining, currentTick,
                currentTick + RESTORE_OBSERVATION_TIMEOUT_TICKS, phase);
        restoreRequests.compute(pendingKey, (key, existing) -> {
            if (existing != null
                    && existing.epoch() == request.epoch()
                    && existing.server() == server
                    && existing.level() == level
                    && existing.token().equals(token)) {
                return existing;
            }
            return request;
        });
    }

    private static void processRestoreRequests(MinecraftServer server) {
        long currentTick = server.getTickCount();
        for (Map.Entry<PendingKey, RestoreRequest> entry : restoreRequests.entrySet()) {
            PendingKey pendingKey = entry.getKey();
            RestoreRequest request = entry.getValue();
            if (request.retryAtTick() > currentTick) {
                continue;
            }
            if (!isRestoreRequestCurrent(pendingKey, request)) {
                if (restoreRequests.remove(pendingKey, request)) {
                    terminateInvalidRequestContext(pendingKey, request.server(), request.token(),
                            "server level changed while an Anchor Core restore was pending");
                }
                continue;
            }
            try {
                processRestoreRequest(pendingKey, request, currentTick);
            } catch (Exception e) {
                if (restoreRequests.remove(pendingKey, request)) {
                    Global.LOGGER.error("Anchor Core 区块恢复验证发生异常: {}",
                            pendingKey.chunkKey(), e);
                    abortTransientRequest(pendingKey, server, request.token(),
                            "unexpected restore validation failure");
                }
            }
        }
    }

    private static void processRestoreRequest(PendingKey pendingKey, RestoreRequest request,
                                              long currentTick) {
        ServerLevel level = request.level();
        ChunkPos chunkPos = pendingKey.chunkKey().chunkPos();
        boolean chunkLoaded = isChunkLoaded(level, chunkPos);
        boolean entityStorageLoaded = chunkLoaded && level.areEntitiesLoaded(chunkPos.toLong());
        var entity = entityStorageLoaded ? level.getEntity(pendingKey.maidId()) : null;
        boolean maidStateValid = entity instanceof EntityMaid maid
                && !maid.isRemoved() && maid.isAlive()
                && maid.level() == level
                && maid.chunkPosition().equals(pendingKey.chunkKey().chunkPos())
                && BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE);

        AnchorRestorePolicy.RestoreStep step = AnchorRestorePolicy.forRestore(
                chunkLoaded, entityStorageLoaded, entity != null, maidStateValid);
        if (step.action() == AnchorRestorePolicy.RestoreAction.WAIT) {
            if (request.waitDeadlineTick() <= currentTick) {
                if (restoreRequests.remove(pendingKey, request)) {
                    abortTransientRequest(pendingKey, request.server(), request.token(),
                            "chunk or entity loading observation timed out");
                }
                return;
            }
            rescheduleRestoreRequest(pendingKey, request, request.attemptsRemaining(),
                    currentTick + 1, step.phase());
            return;
        }
        if (step.action() == AnchorRestorePolicy.RestoreAction.DISCARD) {
            if (restoreRequests.remove(pendingKey, request)) {
                discardInvalidRequest(pendingKey, request.server(), request.token(),
                        "maid state no longer matches the request");
            }
            return;
        }

        if (!renewProvisionalTicket(pendingKey, request.server(), level, request.token(), true)) {
            if (request.attemptsRemaining() > 0) {
                rescheduleRestoreRequest(pendingKey, request, request.attemptsRemaining() - 1,
                        currentTick + RESTORE_RETRY_INTERVAL_TICKS,
                        AnchorRestorePolicy.RestorePhase.WAITING_ENTITIES);
            } else if (restoreRequests.remove(pendingKey, request)) {
                abortTransientRequest(pendingKey, request.server(), request.token(),
                        "provisional ticket renewal retries exhausted");
            }
            return;
        }

        try {
            commitForcedChunkTracking(request.server(), level, pendingKey);
            if (restoreRequests.remove(pendingKey, request)) {
                requestTokens.remove(pendingKey, request.token());
                releaseProvisionalTicket(pendingKey, request.token());
            }
        } catch (Exception e) {
            if (request.attemptsRemaining() > 0) {
                rescheduleRestoreRequest(pendingKey, request, request.attemptsRemaining() - 1,
                        currentTick + RESTORE_RETRY_INTERVAL_TICKS,
                        AnchorRestorePolicy.RestorePhase.WAITING_ENTITIES);
                Global.LOGGER.warn("启用区块强加载失败，将重试: {}", pendingKey.chunkKey(), e);
            } else if (restoreRequests.remove(pendingKey, request)) {
                abortTransientRequest(pendingKey, request.server(), request.token(),
                        "force chunk retries exhausted");
            }
        }
    }

    private static void rescheduleRestoreRequest(PendingKey pendingKey, RestoreRequest request,
                                                 int attemptsRemaining, long retryAtTick,
                                                 AnchorRestorePolicy.RestorePhase phase) {
        restoreRequests.replace(pendingKey, request,
                copyRestoreRequest(request, attemptsRemaining, retryAtTick, phase));
    }

    private static RestoreRequest copyRestoreRequest(RestoreRequest request, int attemptsRemaining,
                                                     long retryAtTick,
                                                     AnchorRestorePolicy.RestorePhase phase) {
        return new RestoreRequest(
                request.epoch(), request.server(), request.level(), request.token(),
                attemptsRemaining, retryAtTick, request.waitDeadlineTick(), phase);
    }

    private static void commitForcedChunkTracking(MinecraftServer server, ServerLevel level,
                                                  PendingKey pendingKey) throws Exception {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Anchor Core ticket commits must run on the server thread");
        }
        if (!isLevelContextCurrent(server, level, pendingKey)) {
            throw new IllegalStateException("Anchor Core ticket commit belongs to a stale server level");
        }

        boolean wasPersisted = isChunkTracked(pendingKey);
        boolean ticketWasActive = isForcedTicketActive(pendingKey, server, level)
                && !formalReleaseRetries.containsKey(pendingKey);
        boolean associationWasPresent = ticketWasActive && isChunkAssociated(pendingKey);
        boolean ticketEstablished = false;
        try {
            if (!ticketWasActive) {
                establishForcedChunkTicket(level, pendingKey);
                ticketEstablished = true;
            }
            if (!wasPersisted) {
                ChunkLoadingData.get(server).updateMaidPosition(
                        pendingKey.maidId(), pendingKey.chunkKey(), true);
            }
            if (!associationWasPresent) {
                ChunkAnchorState state = activeChunkAnchors.computeIfAbsent(
                        pendingKey.chunkKey(), ChunkAnchorState::new);
                state.addMaid(pendingKey.maidId());
            }
            formalReleaseRetries.remove(pendingKey);
            recoveryRetries.remove(pendingKey);
            releaseTeleportPreload(pendingKey);
        } catch (Exception e) {
            if (!associationWasPresent && isChunkAssociated(pendingKey)) {
                removeChunkAssociation(pendingKey);
            }
            if (!wasPersisted && isChunkTracked(pendingKey)) {
                try {
                    ChunkLoadingData.get(server).updateMaidPosition(
                            pendingKey.maidId(), pendingKey.chunkKey(), false);
                } catch (Exception rollbackError) {
                    Global.LOGGER.error("回滚 Anchor Core 持久记录失败: {}",
                            pendingKey.chunkKey(), rollbackError);
                }
            }
            if (ticketEstablished) {
                releaseForcedChunkTicket(server, pendingKey, true);
            }
            throw e;
        }
    }

    private static void establishForcedChunkTicket(ServerLevel level, PendingKey pendingKey) throws Exception {
        ChunkPos chunkPos = pendingKey.chunkKey().chunkPos();
        try {
            boolean added = ForgeChunkManager.forceChunk(
                    level, MaidSpellMod.MOD_ID, pendingKey.maidId(),
                    chunkPos.x, chunkPos.z, true, true);
            if (!added) {
                boolean removed = ForgeChunkManager.forceChunk(
                        level, MaidSpellMod.MOD_ID, pendingKey.maidId(),
                        chunkPos.x, chunkPos.z, false, true);
                boolean rebuilt = removed && ForgeChunkManager.forceChunk(
                        level, MaidSpellMod.MOD_ID, pendingKey.maidId(),
                        chunkPos.x, chunkPos.z, true, true);
                if (!rebuilt) {
                    throw new IllegalStateException(
                            "Could not rebuild an untracked Anchor Core forced chunk ticket");
                }
            }
            activeForcedTickets.put(pendingKey,
                    new ActiveForcedTicket(SERVER_EPOCH.get(), level.getServer(), level));
        } catch (Exception e) {
            releaseForcedChunkTicket(level.getServer(), pendingKey, true);
            throw e;
        }
    }

    private static void acquireTeleportPreload(PendingKey pendingKey, MinecraftServer server,
                                               ServerLevel level) {
        TeleportPreloadTicket existing = teleportPreloads.get(pendingKey);
        UUID token = existing != null
                && existing.epoch() == SERVER_EPOCH.get()
                && existing.server() == server
                && existing.level() == level
                ? existing.token()
                : UUID.randomUUID();

        if (existing != null && !existing.token().equals(token)) {
            releaseTeleportPreload(pendingKey, existing);
            if (teleportPreloads.get(pendingKey) == existing) {
                return;
            }
        }

        try {
            level.getChunkSource().addRegionTicket(
                    TELEPORT_PRELOAD_TICKET_TYPE,
                    pendingKey.chunkKey().chunkPos(),
                    TELEPORT_PRELOAD_TICKET_DISTANCE,
                    token,
                    TELEPORT_PRELOAD_FORCE_TICKS);
            long expiresAtTick = (long) server.getTickCount() + TELEPORT_PRELOAD_LIFETIME_TICKS;
            teleportPreloads.put(pendingKey, new TeleportPreloadTicket(
                    SERVER_EPOCH.get(), server, level, token, expiresAtTick, expiresAtTick));
            Global.LOGGER.debug("已添加女仆 {} 的传送目标短票据: {}",
                    pendingKey.maidId(), pendingKey.chunkKey());
        } catch (Exception e) {
            Global.LOGGER.warn("添加女仆 {} 的传送目标短票据失败: {}",
                    pendingKey.maidId(), pendingKey.chunkKey(), e);
        }
    }

    private static void processTeleportPreloads(MinecraftServer server) {
        long currentTick = server.getTickCount();
        for (Map.Entry<PendingKey, TeleportPreloadTicket> entry
                : new ArrayList<>(teleportPreloads.entrySet())) {
            PendingKey pendingKey = entry.getKey();
            TeleportPreloadTicket ticket = entry.getValue();
            boolean currentContext = ticket.epoch() == SERVER_EPOCH.get()
                    && ticket.server() == server
                    && server.getLevel(pendingKey.chunkKey().dimension()) == ticket.level();
            boolean releaseDue = ticket.expiresAtTick() <= currentTick
                    && ticket.nextReleaseAttemptTick() <= currentTick;
            if (!currentContext || releaseDue) {
                releaseTeleportPreload(pendingKey, ticket);
            }
        }
    }

    private static boolean releaseTeleportPreload(PendingKey pendingKey) {
        TeleportPreloadTicket ticket = teleportPreloads.get(pendingKey);
        return ticket == null || releaseTeleportPreload(pendingKey, ticket);
    }

    private static boolean releaseTeleportPreload(PendingKey pendingKey,
                                                  TeleportPreloadTicket ticket) {
        if (teleportPreloads.get(pendingKey) != ticket) {
            return true;
        }
        if (ticket.server() == activeServer
                && ticket.server().getLevel(pendingKey.chunkKey().dimension()) != ticket.level()) {
            teleportPreloads.remove(pendingKey, ticket);
            return true;
        }

        try {
            ticket.level().getChunkSource().removeRegionTicket(
                    TELEPORT_PRELOAD_TICKET_TYPE,
                    pendingKey.chunkKey().chunkPos(),
                    TELEPORT_PRELOAD_TICKET_DISTANCE,
                    ticket.token(),
                    TELEPORT_PRELOAD_FORCE_TICKS);
            teleportPreloads.remove(pendingKey, ticket);
            return true;
        } catch (Exception e) {
            long retryAtTick = (long) ticket.server().getTickCount()
                    + TICKET_RELEASE_RETRY_INTERVAL_TICKS;
            teleportPreloads.replace(pendingKey, ticket, new TeleportPreloadTicket(
                    ticket.epoch(), ticket.server(), ticket.level(), ticket.token(),
                    ticket.expiresAtTick(), retryAtTick));
            Global.LOGGER.warn("释放女仆 {} 的传送目标短票据失败: {}",
                    pendingKey.maidId(), pendingKey.chunkKey(), e);
            return false;
        }
    }

    private static void renewProvisionalTickets(MinecraftServer server) {
        for (Map.Entry<PendingKey, ProvisionalTicket> entry
                : new ArrayList<>(provisionalTickets.entrySet())) {
            PendingKey pendingKey = entry.getKey();
            ProvisionalTicket ticket = entry.getValue();
            boolean requestStillOwnsTicket = ticket.epoch() == SERVER_EPOCH.get()
                    && ticket.server() == server
                    && server.getLevel(pendingKey.chunkKey().dimension()) == ticket.level()
                    && Objects.equals(requestTokens.get(pendingKey), ticket.token());
            if (requestStillOwnsTicket) {
                renewProvisionalTicket(pendingKey, server, ticket.level(), ticket.token(), false);
            }
        }
    }

    private static void processProvisionalReleaseRetries(MinecraftServer server) {
        long currentTick = server.getTickCount();
        for (Map.Entry<PendingKey, ProvisionalTicket> entry
                : new ArrayList<>(provisionalTickets.entrySet())) {
            PendingKey pendingKey = entry.getKey();
            ProvisionalTicket ticket = entry.getValue();
            boolean requestStillOwnsTicket = ticket.epoch() == SERVER_EPOCH.get()
                    && ticket.server() == server
                    && Objects.equals(requestTokens.get(pendingKey), ticket.token());
            if (requestStillOwnsTicket) {
                continue;
            }
            long retryAt = provisionalReleaseRetries.getOrDefault(pendingKey, Long.MIN_VALUE);
            if (retryAt <= currentTick) {
                releaseProvisionalTicket(pendingKey, ticket.token());
            }
        }
        provisionalReleaseRetries.keySet().removeIf(key -> !provisionalTickets.containsKey(key));
    }

    private static void processFormalReleaseRetries(MinecraftServer server) {
        long currentTick = server.getTickCount();
        for (Map.Entry<PendingKey, FormalReleaseRetry> entry : formalReleaseRetries.entrySet()) {
            PendingKey pendingKey = entry.getKey();
            FormalReleaseRetry retry = entry.getValue();
            if (retry.epoch() != SERVER_EPOCH.get() || retry.server() != server) {
                formalReleaseRetries.remove(pendingKey, retry);
                ActiveForcedTicket activeTicket = activeForcedTickets.get(pendingKey);
                if (activeTicket != null
                        && (activeTicket.epoch() != SERVER_EPOCH.get()
                            || activeTicket.server() != server)) {
                    activeForcedTickets.remove(pendingKey, activeTicket);
                }
                continue;
            }
            if (retry.retryAtTick() > currentTick) {
                continue;
            }
            if (isChunkAssociated(pendingKey)) {
                ServerLevel level = server.getLevel(pendingKey.chunkKey().dimension());
                if (level == null) {
                    ActiveForcedTicket staleTicket = activeForcedTickets.remove(pendingKey);
                    if (staleTicket != null) {
                        scheduleStaleForcedTicketRelease(pendingKey, staleTicket);
                    }
                    formalReleaseRetries.remove(pendingKey, retry);
                    removeChunkAssociation(pendingKey);
                    scheduleRecoveryRetry(pendingKey, server,
                            "dimension disappeared while reconciling a formal ticket");
                    continue;
                }
                try {
                    establishForcedChunkTicket(level, pendingKey);
                    formalReleaseRetries.remove(pendingKey, retry);
                } catch (Exception e) {
                    scheduleFormalReleaseRetry(pendingKey, server);
                    Global.LOGGER.warn("修复 Anchor Core 正式票据状态失败: {}",
                            pendingKey.chunkKey(), e);
                }
                continue;
            }
            releaseForcedChunkTicket(server, pendingKey, true);
        }
    }

    private static void scheduleStaleForcedTicketRelease(PendingKey pendingKey,
                                                         ActiveForcedTicket ticket) {
        if (ticket.epoch() != SERVER_EPOCH.get() || ticket.server() != activeServer) {
            return;
        }
        StaleForcedTicketKey key = new StaleForcedTicketKey(pendingKey, ticket);
        staleForcedTicketReleases.putIfAbsent(key, new StaleForcedTicketRelease(
                ticket.server().getTickCount(), RESTORE_RETRY_ATTEMPTS));
    }

    private static void processStaleForcedTicketReleases(MinecraftServer server) {
        long currentTick = server.getTickCount();
        for (Map.Entry<StaleForcedTicketKey, StaleForcedTicketRelease> entry
                : staleForcedTicketReleases.entrySet()) {
            StaleForcedTicketKey key = entry.getKey();
            StaleForcedTicketRelease release = entry.getValue();
            ActiveForcedTicket ticket = key.ticket();
            if (ticket.epoch() != SERVER_EPOCH.get() || ticket.server() != server) {
                staleForcedTicketReleases.remove(key, release);
                continue;
            }
            if (isStaleForcedTicketReactivated(key)) {
                staleForcedTicketReleases.remove(key, release);
                continue;
            }
            if (release.retryAtTick() > currentTick) {
                continue;
            }

            try {
                removeForcedChunkTicketFromLevel(ticket.level(), key.pendingKey());
                staleForcedTicketReleases.remove(key, release);
                Global.LOGGER.debug("已释放旧 ServerLevel 的 Anchor Core 正式票据: {}",
                        key.pendingKey().chunkKey());
            } catch (Exception e) {
                if (release.attemptsRemaining() > 0) {
                    staleForcedTicketReleases.replace(key, release,
                            new StaleForcedTicketRelease(
                                    currentTick + TICKET_RELEASE_RETRY_INTERVAL_TICKS,
                                    release.attemptsRemaining() - 1));
                } else if (staleForcedTicketReleases.remove(key, release)) {
                    Global.LOGGER.error("释放旧 ServerLevel 的 Anchor Core 正式票据失败，已停止重试: {}",
                            key.pendingKey().chunkKey(), e);
                }
            }
        }
    }

    private static void releaseAllStaleForcedTickets() {
        for (StaleForcedTicketKey key : new ArrayList<>(staleForcedTicketReleases.keySet())) {
            if (isStaleForcedTicketReactivated(key)) {
                continue;
            }
            try {
                removeForcedChunkTicketFromLevel(key.ticket().level(), key.pendingKey());
            } catch (Exception e) {
                Global.LOGGER.warn("服务器关闭时释放旧 ServerLevel 的 Anchor Core 正式票据失败: {}",
                        key.pendingKey().chunkKey(), e);
            }
        }
        staleForcedTicketReleases.clear();
    }

    private static boolean isStaleForcedTicketReactivated(StaleForcedTicketKey key) {
        ActiveForcedTicket activeTicket = activeForcedTickets.get(key.pendingKey());
        ActiveForcedTicket staleTicket = key.ticket();
        return activeTicket != null
                && activeTicket.epoch() == staleTicket.epoch()
                && activeTicket.server() == staleTicket.server()
                && activeTicket.level() == staleTicket.level();
    }

    private static void processPendingLevelRequestTimeouts(MinecraftServer server) {
        long currentTick = server.getTickCount();
        for (Map.Entry<PendingKey, Long> entry : pendingLevelDeadlines.entrySet()) {
            PendingKey pendingKey = entry.getKey();
            long deadlineTick = entry.getValue();
            if (deadlineTick > currentTick) {
                continue;
            }
            PendingLevelRequest request = pendingLevelRequests.get(pendingKey);
            if (request == null) {
                pendingLevelDeadlines.remove(pendingKey, deadlineTick);
                continue;
            }
            if (request.epoch() != SERVER_EPOCH.get()
                    || request.server() != server
                    || !Objects.equals(requestTokens.get(pendingKey), request.token())) {
                pendingLevelRequests.remove(pendingKey, request);
                pendingLevelDeadlines.remove(pendingKey, deadlineTick);
                continue;
            }
            if (request.future().isDone()) {
                pendingLevelDeadlines.replace(pendingKey, deadlineTick,
                        currentTick + TICKET_RELEASE_RETRY_INTERVAL_TICKS);
                continue;
            }

            boolean expired = PlayerRetreatManager.expireRetreatCreationRequest(
                    server, pendingKey.chunkKey().dimension(), request.future(),
                    "Anchor Core restore timed out waiting for the retreat dimension");
            if (!expired && request.future().isDone()) {
                pendingLevelDeadlines.replace(pendingKey, deadlineTick,
                        currentTick + TICKET_RELEASE_RETRY_INTERVAL_TICKS);
                continue;
            }
            if (!pendingLevelRequests.remove(pendingKey, request)) {
                continue;
            }
            pendingLevelDeadlines.remove(pendingKey, deadlineTick);
            Global.LOGGER.warn("Anchor Core 等待动态维度创建超时，将保留记录并稍后重试: {}",
                    pendingKey.chunkKey().dimension().location());
            abortTransientRequest(pendingKey, server, request.token(),
                    "retreat dimension creation timed out");
        }
    }

    private static void processCompletedLevelRequests(MinecraftServer server) {
        CompletedLevelRequest completed;
        int processed = 0;
        int scanned = 0;
        while (processed < MAX_LEVEL_COMPLETIONS_PER_TICK
                && scanned < MAX_LEVEL_COMPLETION_SCANS_PER_TICK
                && (completed = completedLevelRequests.poll()) != null) {
            scanned++;
            PendingLevelRequest request = completed.request();
            if (request.server() != server
                    || pendingLevelRequests.get(completed.pendingKey()) != request) {
                continue;
            }
            processed++;
            finishPendingLevelRequest(completed.pendingKey(), request,
                    completed.level(), completed.throwable());
        }
    }

    private static void scheduleRecoveryRetry(PendingKey pendingKey, MinecraftServer server, String reason) {
        if (!isActiveSession(server) || !isChunkTracked(pendingKey)) {
            return;
        }
        long retryAt = (long) server.getTickCount() + RECOVERY_RETRY_INTERVAL_TICKS;
        recoveryRetries.compute(pendingKey, (key, existing) -> {
            if (existing != null
                    && existing.epoch() == SERVER_EPOCH.get()
                    && existing.server() == server
                    && existing.retryAtTick() <= retryAt) {
                return existing;
            }
            return new RecoveryRetry(SERVER_EPOCH.get(), server, retryAt);
        });
        Global.LOGGER.debug("Anchor Core 持久恢复将在冷却后重试: maid={}, chunk={}, reason={}",
                pendingKey.maidId(), pendingKey.chunkKey(), reason);
    }

    private static void processRecoveryRetries(MinecraftServer server) {
        long currentTick = server.getTickCount();
        for (Map.Entry<PendingKey, RecoveryRetry> entry : recoveryRetries.entrySet()) {
            PendingKey pendingKey = entry.getKey();
            RecoveryRetry retry = entry.getValue();
            if (retry.epoch() != SERVER_EPOCH.get() || retry.server() != server
                    || !isChunkTracked(pendingKey)) {
                recoveryRetries.remove(pendingKey, retry);
                continue;
            }
            if (retry.retryAtTick() > currentTick) {
                continue;
            }
            if (requestTokens.containsKey(pendingKey) || isChunkAssociated(pendingKey)) {
                recoveryRetries.remove(pendingKey, retry);
                continue;
            }
            if (formalReleaseRetries.containsKey(pendingKey)) {
                recoveryRetries.replace(pendingKey, retry,
                        new RecoveryRetry(retry.epoch(), server,
                                currentTick + TICKET_RELEASE_RETRY_INTERVAL_TICKS));
                continue;
            }
            if (recoveryRetries.remove(pendingKey, retry)) {
                restorePersistedChunk(server, pendingKey.maidId(), pendingKey.chunkKey());
            }
        }
    }

    private static void scheduleFormalReleaseRetry(PendingKey pendingKey, MinecraftServer server) {
        if (activeServer != server) {
            return;
        }
        formalReleaseRetries.put(pendingKey, new FormalReleaseRetry(
                SERVER_EPOCH.get(), server,
                (long) server.getTickCount() + TICKET_RELEASE_RETRY_INTERVAL_TICKS));
    }

    private static boolean isRestoreRequestCurrent(PendingKey pendingKey, RestoreRequest request) {
        return restoreRequests.get(pendingKey) == request
                && request.epoch() == SERVER_EPOCH.get()
                && activeServer == request.server()
                && acceptingRequests
                && Objects.equals(requestTokens.get(pendingKey), request.token())
                && isProvisionalTicketCurrent(pendingKey, request.server(), request.level(), request.token())
                && request.server().getLevel(pendingKey.chunkKey().dimension()) == request.level();
    }

    private static boolean isLevelContextCurrent(MinecraftServer server, ServerLevel level,
                                                 PendingKey pendingKey) {
        return activeServer == server && acceptingRequests
                && server.getLevel(pendingKey.chunkKey().dimension()) == level;
    }

    private static boolean isRequestContextCurrent(MinecraftServer server, ServerLevel level,
                                                   PendingKey pendingKey, UUID token) {
        return isRequestOwnerCurrent(server, level, pendingKey, token)
                && isProvisionalTicketCurrent(pendingKey, server, level, token);
    }

    private static boolean ensureRequestTicketCurrent(PendingKey pendingKey, MinecraftServer server,
                                                       ServerLevel level, UUID token, String failureReason) {
        if (!isRequestOwnerCurrent(server, level, pendingKey, token)) {
            terminateInvalidRequestContext(pendingKey, server, token, failureReason);
            return false;
        }
        if (isRequestContextCurrent(server, level, pendingKey, token)) {
            return true;
        }
        abortTransientRequest(pendingKey, server, token, failureReason);
        return false;
    }

    private static boolean isRequestOwnerCurrent(MinecraftServer server, ServerLevel level,
                                                  PendingKey pendingKey, UUID token) {
        return isLevelContextCurrent(server, level, pendingKey)
                && Objects.equals(requestTokens.get(pendingKey), token);
    }

    private static void terminateInvalidRequestContext(PendingKey pendingKey, MinecraftServer server,
                                                       UUID token, String reason) {
        if (activeServer == server && acceptingRequests
                && Objects.equals(requestTokens.get(pendingKey), token)) {
            abortTransientRequest(pendingKey, server, token, reason);
            return;
        }
        requestTokens.remove(pendingKey, token);
        releaseProvisionalTicket(pendingKey, token);
    }

    private static boolean acquireProvisionalTicket(PendingKey pendingKey, MinecraftServer server,
                                                    ServerLevel level, UUID token) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Provisional chunk tickets must run on the server thread");
        }
        if (!isRequestOwnerCurrent(server, level, pendingKey, token)) {
            return false;
        }

        ProvisionalTicket existing = provisionalTickets.get(pendingKey);
        if (existing != null) {
            if (existing.epoch() == SERVER_EPOCH.get()
                    && existing.server() == server
                    && existing.level() == level
                    && existing.token().equals(token)) {
                return renewProvisionalTicket(pendingKey, server, level, token, false);
            }
            if (!releaseProvisionalTicket(pendingKey, existing.token())) {
                return false;
            }
        }

        try {
            level.getChunkSource().addRegionTicket(
                    PROVISIONAL_TICKET_TYPE,
                    pendingKey.chunkKey().chunkPos(),
                    PROVISIONAL_TICKET_DISTANCE,
                    token,
                    PROVISIONAL_TICKET_FORCE_TICKS);
            provisionalTickets.put(pendingKey,
                    new ProvisionalTicket(
                            SERVER_EPOCH.get(), server, level, token,
                            (long) server.getTickCount() + PROVISIONAL_TICKET_RENEW_INTERVAL_TICKS));
            provisionalReleaseRetries.remove(pendingKey);
            return true;
        } catch (Exception e) {
            Global.LOGGER.error("无法为 Anchor Core 恢复请求添加临时实体票据: {}",
                    pendingKey.chunkKey(), e);
            return false;
        }
    }

    private static boolean renewProvisionalTicket(PendingKey pendingKey, MinecraftServer server,
                                                  ServerLevel level, UUID token, boolean force) {
        ProvisionalTicket ticket = provisionalTickets.get(pendingKey);
        if (ticket == null
                || ticket.epoch() != SERVER_EPOCH.get()
                || ticket.server() != server
                || ticket.level() != level
                || !ticket.token().equals(token)) {
            return false;
        }

        long currentTick = server.getTickCount();
        if (!force && ticket.nextRenewTick() > currentTick) {
            return true;
        }

        try {
            level.getChunkSource().addRegionTicket(
                    PROVISIONAL_TICKET_TYPE,
                    pendingKey.chunkKey().chunkPos(),
                    PROVISIONAL_TICKET_DISTANCE,
                    token,
                    PROVISIONAL_TICKET_FORCE_TICKS);
            ProvisionalTicket renewed = new ProvisionalTicket(
                    ticket.epoch(), server, level, token,
                    currentTick + PROVISIONAL_TICKET_RENEW_INTERVAL_TICKS);
            return provisionalTickets.replace(pendingKey, ticket, renewed)
                    || isProvisionalTicketCurrent(pendingKey, server, level, token);
        } catch (Exception e) {
            ProvisionalTicket delayedRetry = new ProvisionalTicket(
                    ticket.epoch(), server, level, token,
                    currentTick + 1);
            provisionalTickets.replace(pendingKey, ticket, delayedRetry);
            Global.LOGGER.warn("续租 Anchor Core 临时实体票据失败: {}",
                    pendingKey.chunkKey(), e);
            return false;
        }
    }

    private static boolean isProvisionalTicketCurrent(PendingKey pendingKey, MinecraftServer server,
                                                       ServerLevel level, UUID token) {
        ProvisionalTicket ticket = provisionalTickets.get(pendingKey);
        return ticket != null
                && ticket.epoch() == SERVER_EPOCH.get()
                && ticket.server() == server
                && ticket.level() == level
                && ticket.token().equals(token);
    }

    private static boolean releaseProvisionalTicket(PendingKey pendingKey) {
        ProvisionalTicket ticket = provisionalTickets.get(pendingKey);
        return ticket == null || releaseProvisionalTicket(pendingKey, ticket.token());
    }

    private static boolean releaseProvisionalTicket(PendingKey pendingKey, UUID expectedToken) {
        ProvisionalTicket ticket = provisionalTickets.get(pendingKey);
        if (ticket == null || !ticket.token().equals(expectedToken)) {
            return true;
        }
        if (ticket.server() == activeServer
                && ticket.server().getLevel(pendingKey.chunkKey().dimension()) != ticket.level()) {
            provisionalTickets.remove(pendingKey, ticket);
            provisionalReleaseRetries.remove(pendingKey);
            return true;
        }

        try {
            ticket.level().getChunkSource().removeRegionTicket(
                    PROVISIONAL_TICKET_TYPE,
                    pendingKey.chunkKey().chunkPos(),
                    PROVISIONAL_TICKET_DISTANCE,
                    ticket.token(),
                    PROVISIONAL_TICKET_FORCE_TICKS);
        } catch (Exception e) {
            provisionalReleaseRetries.put(pendingKey,
                    (long) ticket.server().getTickCount() + TICKET_RELEASE_RETRY_INTERVAL_TICKS);
            Global.LOGGER.warn("释放 Anchor Core 临时实体票据失败: {}",
                    pendingKey.chunkKey(), e);
            return false;
        }
        provisionalTickets.remove(pendingKey, ticket);
        provisionalReleaseRetries.remove(pendingKey);
        return true;
    }

    private static void abortTransientRequest(PendingKey pendingKey, MinecraftServer server,
                                              UUID token, String reason) {
        if (activeServer != server || !acceptingRequests
                || !requestTokens.remove(pendingKey, token)) {
            releaseProvisionalTicket(pendingKey, token);
            return;
        }
        cancelPendingRequestState(pendingKey);
        releaseProvisionalTicket(pendingKey, token);
        releaseForcedChunkTicket(server, pendingKey);
        removeChunkAssociation(pendingKey);
        if (isChunkTracked(pendingKey)) {
            scheduleRecoveryRetry(pendingKey, server, reason);
            Global.LOGGER.error("Anchor Core 区块恢复暂时失败，已保留持久记录并安排重试: "
                            + "maid={}, chunk={}, reason={}",
                    pendingKey.maidId(), pendingKey.chunkKey(), reason);
        } else {
            Global.LOGGER.error("Anchor Core 区块请求暂时失败: maid={}, chunk={}, reason={}",
                    pendingKey.maidId(), pendingKey.chunkKey(), reason);
        }
    }

    private static void discardInvalidRequest(PendingKey pendingKey, MinecraftServer server,
                                              UUID token, String reason) {
        if (activeServer != server || !acceptingRequests
                || !requestTokens.remove(pendingKey, token)) {
            releaseProvisionalTicket(pendingKey, token);
            return;
        }
        cancelPendingRequestState(pendingKey);
        releaseProvisionalTicket(pendingKey, token);
        recoveryRetries.remove(pendingKey);
        removeChunkTracking(server, pendingKey);
        Global.LOGGER.warn("已清理失效的女仆区块加载记录: maid={}, chunk={}, reason={}",
                pendingKey.maidId(), pendingKey.chunkKey(), reason);
    }

    private static void removeChunkTracking(MinecraftServer server, PendingKey pendingKey) {
        recoveryRetries.remove(pendingKey);
        releaseForcedChunkTicket(server, pendingKey);
        ChunkLoadingData.get(server).updateMaidPosition(
                pendingKey.maidId(), pendingKey.chunkKey(), false);
        removeChunkAssociation(pendingKey);
    }

    private static boolean releaseForcedChunkTicket(MinecraftServer server, PendingKey pendingKey) {
        return releaseForcedChunkTicket(server, pendingKey, false);
    }

    private static boolean releaseForcedChunkTicket(MinecraftServer server, PendingKey pendingKey,
                                                    boolean forceAttempt) {
        if (!forceAttempt && !activeForcedTickets.containsKey(pendingKey)
                && !formalReleaseRetries.containsKey(pendingKey)) {
            return true;
        }
        ActiveForcedTicket activeTicket = activeForcedTickets.get(pendingKey);
        boolean activeTicketBelongsToSession = activeTicket != null
                && activeTicket.epoch() == SERVER_EPOCH.get()
                && activeTicket.server() == server;
        ServerLevel level = activeTicketBelongsToSession
                ? activeTicket.level()
                : server.getLevel(pendingKey.chunkKey().dimension());
        if (level == null) {
            ActiveForcedTicket staleTicket = activeTicket != null
                    && activeForcedTickets.remove(pendingKey, activeTicket)
                    ? activeTicket
                    : null;
            if (staleTicket != null) {
                scheduleStaleForcedTicketRelease(pendingKey, staleTicket);
            }
            formalReleaseRetries.remove(pendingKey);
            return true;
        }
        try {
            removeForcedChunkTicketFromLevel(level, pendingKey);
            if (activeTicket != null) {
                activeForcedTickets.remove(pendingKey, activeTicket);
            }
            formalReleaseRetries.remove(pendingKey);
            return true;
        } catch (Exception e) {
            scheduleFormalReleaseRetry(pendingKey, server);
            Global.LOGGER.warn("释放 Anchor Core 区块强加载票据失败: {}",
                    pendingKey.chunkKey(), e);
            return false;
        }
    }

    private static void removeForcedChunkTicketFromLevel(ServerLevel level,
                                                         PendingKey pendingKey) {
        ChunkPos chunkPos = pendingKey.chunkKey().chunkPos();
        boolean removed = ForgeChunkManager.forceChunk(
                level, MaidSpellMod.MOD_ID, pendingKey.maidId(),
                chunkPos.x, chunkPos.z, false, true);
        if (removed) {
            return;
        }

        boolean rebuilt = ForgeChunkManager.forceChunk(
                level, MaidSpellMod.MOD_ID, pendingKey.maidId(),
                chunkPos.x, chunkPos.z, true, true);
        boolean removedAfterRebuild = rebuilt && ForgeChunkManager.forceChunk(
                level, MaidSpellMod.MOD_ID, pendingKey.maidId(),
                chunkPos.x, chunkPos.z, false, true);
        if (!removedAfterRebuild) {
            throw new IllegalStateException(
                    "Could not reconcile an Anchor Core forced chunk ticket before release");
        }
    }

    private static void removeChunkAssociation(PendingKey pendingKey) {
        ChunkAnchorState state = activeChunkAnchors.get(pendingKey.chunkKey());
        if (state != null) {
            state.removeMaid(pendingKey.maidId());
            if (state.hasNoMaids()) {
                activeChunkAnchors.remove(pendingKey.chunkKey(), state);
            }
        }
    }

    private static boolean isChunkAssociated(PendingKey pendingKey) {
        ChunkAnchorState state = activeChunkAnchors.get(pendingKey.chunkKey());
        return state != null && state.hasMaid(pendingKey.maidId());
    }

    private static boolean isForcedTicketActive(PendingKey pendingKey, MinecraftServer server,
                                                ServerLevel level) {
        ActiveForcedTicket ticket = activeForcedTickets.get(pendingKey);
        if (ticket == null) {
            return false;
        }
        boolean current = ticket.epoch() == SERVER_EPOCH.get()
                && ticket.server() == server
                && ticket.level() == level;
        if (!current && activeForcedTickets.remove(pendingKey, ticket)) {
            scheduleStaleForcedTicketRelease(pendingKey, ticket);
        }
        return current;
    }

    private static boolean isChunkTracked(PendingKey pendingKey) {
        Set<ChunkKey> chunks = maidChunkPositions.get(pendingKey.maidId());
        return chunks != null && chunks.contains(pendingKey.chunkKey());
    }

    private static boolean isChunkLoaded(ServerLevel level, ChunkPos chunkPos) {
        return level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != null;
    }

    private static void cancelRequestState(PendingKey pendingKey) {
        requestTokens.remove(pendingKey);
        cancelPendingRequestState(pendingKey);
        releaseProvisionalTicket(pendingKey);
        recoveryRetries.remove(pendingKey);
    }

    private static void cancelPendingRequestState(PendingKey pendingKey) {
        restoreRequests.remove(pendingKey);
        pendingLevelRequests.remove(pendingKey);
        pendingLevelDeadlines.remove(pendingKey);
    }

    private static void cancelMaidRequests(UUID maidId) {
        requestTokens.keySet().removeIf(key -> key.maidId().equals(maidId));
        restoreRequests.keySet().removeIf(key -> key.maidId().equals(maidId));
        pendingLevelRequests.keySet().removeIf(key -> key.maidId().equals(maidId));
        pendingLevelDeadlines.keySet().removeIf(key -> key.maidId().equals(maidId));
        recoveryRetries.keySet().removeIf(key -> key.maidId().equals(maidId));
        for (Map.Entry<PendingKey, ProvisionalTicket> entry : provisionalTickets.entrySet()) {
            if (entry.getKey().maidId().equals(maidId)) {
                releaseProvisionalTicket(entry.getKey(), entry.getValue().token());
            }
        }
        for (Map.Entry<PendingKey, TeleportPreloadTicket> entry
                : new ArrayList<>(teleportPreloads.entrySet())) {
            if (entry.getKey().maidId().equals(maidId)) {
                releaseTeleportPreload(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void cancelAllRequests() {
        List<Map.Entry<PendingKey, ProvisionalTicket>> tickets = new ArrayList<>(provisionalTickets.entrySet());
        List<Map.Entry<PendingKey, TeleportPreloadTicket>> preloads =
                new ArrayList<>(teleportPreloads.entrySet());
        requestTokens.clear();
        restoreRequests.clear();
        pendingLevelRequests.clear();
        pendingLevelDeadlines.clear();
        completedLevelRequests.clear();
        recoveryRetries.clear();
        tickets.forEach(entry -> releaseProvisionalTicket(entry.getKey(), entry.getValue().token()));
        preloads.forEach(entry -> releaseTeleportPreload(entry.getKey(), entry.getValue()));
    }

    private static boolean isActiveSession(MinecraftServer server) {
        return server != null && activeServer == server && acceptingRequests;
    }

    private static void executeOnServer(MinecraftServer server, Runnable action) {
        if (server.isSameThread()) {
            action.run();
        } else {
            server.execute(action);
        }
    }

    private static boolean requestRetreatDimension(MinecraftServer server, PendingKey pendingKey, UUID token) {
        PendingLevelRequest existing = pendingLevelRequests.get(pendingKey);
        if (existing != null
                && existing.epoch() == SERVER_EPOCH.get()
                && existing.server() == server
                && existing.token().equals(token)) {
            return true;
        }
        if (existing != null) {
            pendingLevelRequests.remove(pendingKey, existing);
        }

        try {
            CompletableFuture<ServerLevel> future = PlayerRetreatManager.getOrCreateRetreatByKeyAsync(
                    server, pendingKey.chunkKey().dimension(), null);
            PendingLevelRequest request = new PendingLevelRequest(
                    SERVER_EPOCH.get(), server, token, future);
            pendingLevelRequests.put(pendingKey, request);
            pendingLevelDeadlines.put(pendingKey,
                    (long) server.getTickCount() + LEVEL_REQUEST_TIMEOUT_TICKS);
            future.whenComplete((level, throwable) -> completedLevelRequests.add(
                    new CompletedLevelRequest(pendingKey, request, level, throwable)));
            return true;
        } catch (Exception e) {
            Global.LOGGER.error("请求创建 Anchor Core 所在归隐维度失败: {}",
                    pendingKey.chunkKey().dimension().location(), e);
            abortTransientRequest(pendingKey, server, token,
                    "retreat dimension creation could not be queued");
            return false;
        }
    }

    private static void finishPendingLevelRequest(PendingKey pendingKey, PendingLevelRequest request,
                                                  ServerLevel level, Throwable throwable) {
        if (pendingLevelRequests.get(pendingKey) != request) {
            return;
        }
        if (request.epoch() != SERVER_EPOCH.get()
                || activeServer != request.server()
                || !acceptingRequests
                || !Objects.equals(requestTokens.get(pendingKey), request.token())) {
            pendingLevelRequests.remove(pendingKey, request);
            pendingLevelDeadlines.remove(pendingKey);
            requestTokens.remove(pendingKey, request.token());
            return;
        }
        if (throwable != null || level == null
                || request.server().getLevel(pendingKey.chunkKey().dimension()) != level) {
            pendingLevelRequests.remove(pendingKey, request);
            pendingLevelDeadlines.remove(pendingKey);
            Global.LOGGER.error("Anchor Core 等待归隐维度创建失败: {}",
                    pendingKey.chunkKey().dimension().location(), throwable);
            abortTransientRequest(pendingKey, request.server(), request.token(),
                    "retreat dimension creation failed");
            return;
        }
        if (pendingLevelRequests.remove(pendingKey, request)) {
            pendingLevelDeadlines.remove(pendingKey);
            restorePersistedChunk(request.server(), pendingKey.maidId(), pendingKey.chunkKey());
        }
    }

    private static boolean restorePersistedChunk(MinecraftServer server, UUID maidId, ChunkKey chunkKey) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Persisted chunk restoration must run on the server thread");
        }
        if (!isActiveSession(server)) {
            return false;
        }

        PendingKey pendingKey = new PendingKey(maidId, chunkKey);
        recoveryRetries.remove(pendingKey);
        UUID token = requestTokens.computeIfAbsent(pendingKey, key -> UUID.randomUUID());
        ServerLevel level = server.getLevel(chunkKey.dimension());
        if (AnchorRestorePolicy.forDimension(level != null) == AnchorRestorePolicy.Decision.RETRY) {
            if (TheRetreatDimension.isRetreatDimension(chunkKey.dimension().location())) {
                return requestRetreatDimension(server, pendingKey, token);
            }
            abortTransientRequest(pendingKey, server, token,
                    "dimension is temporarily unavailable");
            return true;
        }

        if (!acquireProvisionalTicket(pendingKey, server, level, token)) {
            abortTransientRequest(pendingKey, server, token,
                    "failed to acquire provisional entity ticket");
            return false;
        }

        scheduleRestoreRequest(
                pendingKey, server, level, token, RESTORE_RETRY_ATTEMPTS,
                isChunkLoaded(level, chunkKey.chunkPos())
                        ? AnchorRestorePolicy.RestorePhase.WAITING_ENTITIES
                        : AnchorRestorePolicy.RestorePhase.WAITING_CHUNK);
        return true;
    }
    
    // ===== 全局数据保存类 =====
    
    /**
     * 全局区块加载数据保存类
     * 直接操作静态变量 maidChunkPositions，只负责序列化/反序列化
     */
    public static class ChunkLoadingData extends SavedData {
        private static final String DATA_NAME = "maidspell_chunk_loading";
        
        public ChunkLoadingData() {
        }
        
        public ChunkLoadingData(CompoundTag tag) {
            load(tag);
        }
        
        @SuppressWarnings("removal")
        public static ChunkLoadingData load(CompoundTag tag) {
            ChunkLoadingData data = new ChunkLoadingData();
            
            // 从 NBT 加载数据到静态变量 maidChunkPositions
            CompoundTag maidsTag = tag.getCompound("maids");
            int loadedCount = 0;
            
            for (String uuidStr : maidsTag.getAllKeys()) {
                try {
                    UUID maidId = UUID.fromString(uuidStr);
                    CompoundTag maidTag = maidsTag.getCompound(uuidStr);
                    
                    // 加载该女仆的所有区块
                    CompoundTag chunksTag = maidTag.getCompound("chunks");
                    Set<ChunkKey> chunks = ConcurrentHashMap.newKeySet();
                    
                    for (String chunkIdxStr : chunksTag.getAllKeys()) {
                        CompoundTag chunkTag = chunksTag.getCompound(chunkIdxStr);
                        
                        // 读取区块位置
                        int chunkX = chunkTag.getInt("x");
                        int chunkZ = chunkTag.getInt("z");
                        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                        
                        // 读取维度
                        String dimensionStr = chunkTag.getString("dimension");
                        ResourceKey<Level> dimension = ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            new ResourceLocation(dimensionStr)
                        );
                        ChunkKey chunkKey = new ChunkKey(chunkPos, dimension);
                        chunks.add(chunkKey);
                    }
                    
                    if (!chunks.isEmpty()) {
                        maidChunkPositions.put(maidId, chunks);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    Global.LOGGER.error("加载女仆 {} 的区块数据时发生错误", uuidStr, e);
                }
            }
            
            Global.LOGGER.info("成功加载 {} 个女仆的区块加载数据", loadedCount);
            return data;
        }
        
        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            CompoundTag maidsTag = new CompoundTag();
            
            // 从静态变量 maidChunkPositions 保存数据
            for (Map.Entry<UUID, Set<ChunkKey>> entry : maidChunkPositions.entrySet()) {
                UUID maidId = entry.getKey();
                Set<ChunkKey> chunks = entry.getValue();
                
                if (chunks.isEmpty()) {
                    continue;
                }
                
                CompoundTag maidTag = new CompoundTag();
                CompoundTag chunksTag = new CompoundTag();
                
                // 保存该女仆的所有区块
                int idx = 0;
                for (ChunkKey chunkKey : chunks) {
                    CompoundTag chunkTag = new CompoundTag();
                    chunkTag.putInt("x", chunkKey.chunkPos().x);
                    chunkTag.putInt("z", chunkKey.chunkPos().z);
                    chunkTag.putString("dimension", chunkKey.dimension().location().toString());
                    
                    chunksTag.put(String.valueOf(idx++), chunkTag);
                }
                
                maidTag.put("chunks", chunksTag);
                maidsTag.put(maidId.toString(), maidTag);
            }

            tag.put("maids", maidsTag);
            Global.LOGGER.debug("成功保存 {} 个女仆的区块加载数据", maidChunkPositions.size());
            return tag;
        }
        
        /**
         * 更新女仆的区块位置
         * @param maidId 女仆ID
         * @param info 区块信息
         * @param add true=添加，false=移除
         */
        public void updateMaidPosition(UUID maidId, ChunkKey info, boolean add) {
            if (add) {
                // 添加区块
                maidChunkPositions.computeIfAbsent(maidId, k -> ConcurrentHashMap.newKeySet()).add(info);
            } else {
                // 移除区块
                Set<ChunkKey> chunks = maidChunkPositions.get(maidId);
                if (chunks != null) {
                    chunks.remove(info);
                    // 如果该女仆没有任何区块了，移除整个记录
                    if (chunks.isEmpty()) {
                        maidChunkPositions.remove(maidId);
                    }
                }
            }
            setDirty();
        }
        
        public static ChunkLoadingData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                ChunkLoadingData::load, 
                ChunkLoadingData::new, 
                DATA_NAME
            );
        }
    }

    
    
    // ===== 服务器生命周期 =====

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        acceptingRequests = false;
        releaseAllStaleForcedTickets();
        SERVER_EPOCH.incrementAndGet();
        cancelAllRequests();
        provisionalTickets.clear();
        provisionalReleaseRetries.clear();
        teleportPreloads.clear();
        pendingLevelRequests.clear();
        pendingLevelDeadlines.clear();
        completedLevelRequests.clear();
        recoveryRetries.clear();
        formalReleaseRetries.clear();
        activeForcedTickets.clear();
        staleForcedTicketReleases.clear();
        maidChunkPositions.clear();
        activeChunkAnchors.clear();
        activeServer = event.getServer();
        acceptingRequests = true;
    }

    
    /**
     * 服务器启动时加载保存的区块加载数据
     * ChunkLoadingData.get() 会自动触发 load()，数据会直接加载到 maidChunkPositions
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        if (!isActiveSession(server)) {
            return;
        }

        try {
            // 触发数据加载，load() 方法会自动将数据加载到 maidChunkPositions
            ChunkLoadingData.get(server);
            
            if (!maidChunkPositions.isEmpty()) {
                Global.LOGGER.info("服务器启动时发现 {} 个女仆的区块加载配置", maidChunkPositions.size());
            }
        } catch (Exception e) {
            Global.LOGGER.error("服务器启动时加载区块加载数据发生错误", e);
        }
    }
    
    /**
     * 服务器完全启动后恢复区块加载状态
     * 在这里统一恢复所有区块加载，避免每个玩家登录时重复恢复
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!isActiveSession(server) || maidChunkPositions.isEmpty()) {
            return;
        }
        
        Global.LOGGER.info("服务器启动完成，开始恢复 {} 个女仆的区块加载配置...", maidChunkPositions.size());

        // 统一恢复所有区块加载
        int restoredCount = 0;
        Map<UUID, Set<ChunkKey>> positionsSnapshot = new HashMap<>();
        maidChunkPositions.forEach((maidId, chunks) -> positionsSnapshot.put(maidId, new HashSet<>(chunks)));
        for (Map.Entry<UUID, Set<ChunkKey>> entry : positionsSnapshot.entrySet()) {
            UUID maidId = entry.getKey();
            Set<ChunkKey> chunks = entry.getValue();
            
            // 恢复该女仆的所有区块
            for (ChunkKey info : chunks) {
                if (restorePersistedChunk(server, maidId, info)) {
                    restoredCount++;
                }
            }
        }

        Global.LOGGER.info("服务器启动时提交了 {} 个区块加载恢复请求", restoredCount);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (activeServer != server) {
            return;
        }

        acceptingRequests = false;
        releaseAllStaleForcedTickets();
        SERVER_EPOCH.incrementAndGet();
        cancelAllRequests();
        activeChunkAnchors.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (activeServer != event.getServer()) {
            return;
        }

        acceptingRequests = false;
        releaseAllStaleForcedTickets();
        SERVER_EPOCH.incrementAndGet();
        cancelAllRequests();
        provisionalTickets.clear();
        provisionalReleaseRetries.clear();
        teleportPreloads.clear();
        pendingLevelRequests.clear();
        pendingLevelDeadlines.clear();
        completedLevelRequests.clear();
        recoveryRetries.clear();
        formalReleaseRetries.clear();
        activeForcedTickets.clear();
        staleForcedTicketReleases.clear();
        maidChunkPositions.clear();
        activeChunkAnchors.clear();
        activeServer = null;
    }

    
}
