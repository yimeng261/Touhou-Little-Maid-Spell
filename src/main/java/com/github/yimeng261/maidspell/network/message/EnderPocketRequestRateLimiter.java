package com.github.yimeng261.maidspell.network.message;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 末影腰包界面请求的按玩家限流
 */
final class EnderPocketRequestRateLimiter {
    private static final long LIST_REQUEST_COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
    private static final long OPEN_REQUEST_COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final Map<ServerPlayer, RateLimitState> RATE_LIMITS = new WeakHashMap<>();

    private EnderPocketRequestRateLimiter() {
    }

    static boolean tryAcquireList(ServerPlayer player) {
        return tryAcquire(player, true);
    }

    static boolean tryAcquireOpen(ServerPlayer player) {
        return tryAcquire(player, false);
    }

    private static boolean tryAcquire(ServerPlayer player, boolean listRequest) {
        long now = System.nanoTime();
        synchronized (RATE_LIMITS) {
            RateLimitState state = RATE_LIMITS.getOrDefault(player, RateLimitState.EMPTY);
            long lastRequest = listRequest ? state.lastListRequestNanos : state.lastOpenRequestNanos;
            long cooldown = listRequest ? LIST_REQUEST_COOLDOWN_NANOS : OPEN_REQUEST_COOLDOWN_NANOS;
            if (lastRequest != 0L && now - lastRequest < cooldown) {
                return false;
            }

            RATE_LIMITS.put(player, listRequest
                    ? new RateLimitState(now, state.lastOpenRequestNanos)
                    : new RateLimitState(state.lastListRequestNanos, now));
            return true;
        }
    }

    private record RateLimitState(long lastListRequestNanos, long lastOpenRequestNanos) {
        private static final RateLimitState EMPTY = new RateLimitState(0L, 0L);
    }
}
