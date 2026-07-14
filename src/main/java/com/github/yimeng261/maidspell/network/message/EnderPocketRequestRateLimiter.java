package com.github.yimeng261.maidspell.network.message;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 末影腰包界面请求的按玩家、按请求类型限流
 */
final class EnderPocketRequestRateLimiter {
    enum Action {
        LIST(TimeUnit.MILLISECONDS.toNanos(200)),
        HUD(TimeUnit.MILLISECONDS.toNanos(500)),
        OPEN(TimeUnit.MILLISECONDS.toNanos(250)),
        TELEPORT(TimeUnit.SECONDS.toNanos(1));

        private final long cooldownNanos;

        Action(long cooldownNanos) {
            this.cooldownNanos = cooldownNanos;
        }
    }

    private static final Map<ServerPlayer, long[]> RATE_LIMITS = new WeakHashMap<>();

    private EnderPocketRequestRateLimiter() {
    }

    static boolean tryAcquire(ServerPlayer player, Action action) {
        long now = System.nanoTime();
        synchronized (RATE_LIMITS) {
            long[] lastRequests = RATE_LIMITS.computeIfAbsent(player, p -> new long[Action.values().length]);
            long lastRequest = lastRequests[action.ordinal()];
            if (lastRequest != 0L && now - lastRequest < action.cooldownNanos) {
                return false;
            }
            lastRequests[action.ordinal()] = now;
            return true;
        }
    }
}
