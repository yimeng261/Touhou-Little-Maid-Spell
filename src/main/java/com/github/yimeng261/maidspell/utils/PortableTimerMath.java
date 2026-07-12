package com.github.yimeng261.maidspell.utils;

/**
 * Clock-independent arithmetic for migrating persisted game-time timers.
 */
public final class PortableTimerMath {
    private PortableTimerMath() {
    }

    public static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0 && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    public static long saturatingSubtract(long left, long right) {
        if (right > 0 && left < Long.MIN_VALUE + right) {
            return Long.MIN_VALUE;
        }
        if (right < 0 && left > Long.MAX_VALUE + right) {
            return Long.MAX_VALUE;
        }
        return left - right;
    }

    public static long clampRemaining(long remainingTicks, long maxRemainingTicks, long expiredGraceTicks) {
        long maximum = Math.max(0L, maxRemainingTicks);
        if (maximum == 0L) {
            return 0L;
        }
        if (remainingTicks <= 0L) {
            return Math.min(maximum, Math.max(0L, expiredGraceTicks));
        }
        return Math.min(remainingTicks, maximum);
    }

    public static long migrateDeadline(long oldDeadline, long oldNow, long newNow,
                                       long maxRemainingTicks, long expiredGraceTicks) {
        long remaining = saturatingSubtract(oldDeadline, oldNow);
        long boundedRemaining = clampRemaining(remaining, maxRemainingTicks, expiredGraceTicks);
        return saturatingAdd(newNow, boundedRemaining);
    }

    public static long migrateTimestamp(long legacyTimestamp, long legacyNow, long serverNow,
                                        long maxAge, long graceTicks) {
        long boundedMaxAge = Math.max(0L, maxAge);
        long legacyDeadline = saturatingAdd(legacyTimestamp, boundedMaxAge);
        long serverDeadline = migrateDeadline(
            legacyDeadline, legacyNow, serverNow, boundedMaxAge, graceTicks);
        return saturatingSubtract(serverDeadline, boundedMaxAge);
    }
}
