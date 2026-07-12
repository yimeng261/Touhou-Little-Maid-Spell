package com.github.yimeng261.maidspell.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortableTimerMathTest {
    @Test
    void saturatingArithmeticClampsAtLongExtremes() {
        assertEquals(Long.MAX_VALUE, PortableTimerMath.saturatingAdd(Long.MAX_VALUE - 2, 10));
        assertEquals(Long.MIN_VALUE, PortableTimerMath.saturatingAdd(Long.MIN_VALUE + 2, -10));
        assertEquals(42L, PortableTimerMath.saturatingAdd(40L, 2L));

        assertEquals(Long.MIN_VALUE, PortableTimerMath.saturatingSubtract(Long.MIN_VALUE, 1L));
        assertEquals(Long.MAX_VALUE, PortableTimerMath.saturatingSubtract(Long.MAX_VALUE, -1L));
        assertEquals(38L, PortableTimerMath.saturatingSubtract(40L, 2L));
    }

    @Test
    void deadlineMigrationPreservesRemainingTimeOnTheSameClock() {
        assertEquals(1_100L,
            PortableTimerMath.migrateDeadline(1_100L, 1_000L, 1_000L, 400L, 20L));
    }

    @Test
    void deadlineMigrationPreservesRemainingTimeAcrossClockDomains() {
        assertEquals(5_100L,
            PortableTimerMath.migrateDeadline(1_100L, 1_000L, 5_000L, 400L, 20L));
    }

    @Test
    void expiredDeadlineReceivesOnlyTheConfiguredGrace() {
        assertEquals(5_020L,
            PortableTimerMath.migrateDeadline(900L, 1_000L, 5_000L, 400L, 20L));
    }

    @Test
    void deadlineMigrationCapsExcessiveRemainingTime() {
        assertEquals(5_400L,
            PortableTimerMath.migrateDeadline(10_000L, 1_000L, 5_000L, 400L, 20L));
        assertEquals(5_000L,
            PortableTimerMath.migrateDeadline(10_000L, 1_000L, 5_000L, 0L, 20L));
    }

    @Test
    void timestampMigrationPreservesValidAge() {
        long migrated = PortableTimerMath.migrateTimestamp(900L, 1_000L, 5_000L, 2_400L, 20L);
        assertEquals(100L, PortableTimerMath.saturatingSubtract(5_000L, migrated));
    }

    @Test
    void timestampMigrationClampsFutureAndExpiryBoundaries() {
        long future = PortableTimerMath.migrateTimestamp(1_100L, 1_000L, 5_000L, 2_400L, 20L);
        assertEquals(0L, PortableTimerMath.saturatingSubtract(5_000L, future));

        long justActive = PortableTimerMath.migrateTimestamp(-1_399L, 1_000L, 5_000L, 2_400L, 20L);
        assertEquals(2_399L, PortableTimerMath.saturatingSubtract(5_000L, justActive));

        long expired = PortableTimerMath.migrateTimestamp(-1_400L, 1_000L, 5_000L, 2_400L, 20L);
        assertEquals(2_380L, PortableTimerMath.saturatingSubtract(5_000L, expired));

        long farExpired = PortableTimerMath.migrateTimestamp(Long.MIN_VALUE, 1_000L, 5_000L, 2_400L, 20L);
        assertEquals(2_380L, PortableTimerMath.saturatingSubtract(5_000L, farExpired));
    }
}
