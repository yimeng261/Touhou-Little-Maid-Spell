package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Client-to-server requests for the Ender Pocket UI. */
public final class EnderPocketRequestMessage {
    private static final long LIST_REQUEST_COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
    private static final long OPEN_REQUEST_COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final Map<ServerPlayer, RateLimitState> RATE_LIMITS =
            Collections.synchronizedMap(new WeakHashMap<>());

    public enum Action {
        REQUEST_MAID_LIST,
        OPEN_MAID_INVENTORY
    }

    private final Action action;
    private final boolean fromMaidBackpack;
    private final UUID maidUuid;

    private EnderPocketRequestMessage(Action action, boolean fromMaidBackpack, UUID maidUuid) {
        this.action = action;
        this.fromMaidBackpack = fromMaidBackpack;
        this.maidUuid = maidUuid;
    }

    public static EnderPocketRequestMessage requestMaidList() {
        return new EnderPocketRequestMessage(Action.REQUEST_MAID_LIST, false, null);
    }

    public static EnderPocketRequestMessage requestMaidListFromBackpack() {
        return new EnderPocketRequestMessage(Action.REQUEST_MAID_LIST, true, null);
    }

    public static EnderPocketRequestMessage openMaidInventory(UUID maidUuid) {
        return new EnderPocketRequestMessage(Action.OPEN_MAID_INVENTORY, false, maidUuid);
    }

    public static void encode(EnderPocketRequestMessage message, FriendlyByteBuf buf) {
        buf.writeEnum(message.action);
        switch (message.action) {
            case REQUEST_MAID_LIST -> buf.writeBoolean(message.fromMaidBackpack);
            case OPEN_MAID_INVENTORY -> buf.writeUUID(message.maidUuid);
        }
    }

    public static EnderPocketRequestMessage decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        return switch (action) {
            case REQUEST_MAID_LIST -> new EnderPocketRequestMessage(action, buf.readBoolean(), null);
            case OPEN_MAID_INVENTORY -> new EnderPocketRequestMessage(action, false, buf.readUUID());
        };
    }

    public static void handle(EnderPocketRequestMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(message, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void handleServer(EnderPocketRequestMessage message, ServerPlayer player) {
        if (player == null || !tryAcquire(player, message.action)) {
            return;
        }

        switch (message.action) {
            case REQUEST_MAID_LIST -> NetworkHandler.CHANNEL.sendTo(
                    EnderPocketDataMessage.response(
                            EnderPocketService.getPlayerEnderPocketMaids(player),
                            message.fromMaidBackpack
                    ),
                    player.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT
            );
            case OPEN_MAID_INVENTORY -> EnderPocketService.openMaidInventory(player, message.maidUuid);
        }
    }

    private static boolean tryAcquire(ServerPlayer player, Action action) {
        long now = System.nanoTime();
        synchronized (RATE_LIMITS) {
            RateLimitState state = RATE_LIMITS.getOrDefault(player, RateLimitState.EMPTY);
            long lastRequest = action == Action.REQUEST_MAID_LIST
                    ? state.lastListRequestNanos
                    : state.lastOpenRequestNanos;
            long cooldown = action == Action.REQUEST_MAID_LIST
                    ? LIST_REQUEST_COOLDOWN_NANOS
                    : OPEN_REQUEST_COOLDOWN_NANOS;
            if (lastRequest != 0L && now - lastRequest < cooldown) {
                return false;
            }

            RATE_LIMITS.put(player, action == Action.REQUEST_MAID_LIST
                    ? new RateLimitState(now, state.lastOpenRequestNanos)
                    : new RateLimitState(state.lastListRequestNanos, now));
            return true;
        }
    }

    private record RateLimitState(long lastListRequestNanos, long lastOpenRequestNanos) {
        private static final RateLimitState EMPTY = new RateLimitState(0L, 0L);
    }
}
