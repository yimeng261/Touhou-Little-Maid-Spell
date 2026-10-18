package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WinefoxHighDamageGuardTest {
    @Test
    void smallHitDoesNotConsumeTheGuard() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        assertEquals(1.0D, guard.multiplier(0L));
        guard.recordEffectiveHit(100.0F, WinefoxHighDamageGuard.threshold(600.0D), 0L);
        assertEquals(0.7D, guard.multiplier(10L));
        guard.recordEffectiveHit(3.0F, WinefoxHighDamageGuard.threshold(600.0D), 20L);
        assertTrue(guard.pending());
        assertEquals(33.6D, 48.0D * guard.multiplier(20L), 0.0001D);
    }

    @Test
    void repeatedHighHitsRefreshWithoutStacking() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        for (int i = 0; i < 5; i++) {
            long now = i * 10L;
            guard.recordEffectiveHit(1_000_000_000.0F, WinefoxHighDamageGuard.threshold(600.0D), now);
            assertEquals(0.7D, guard.multiplier(now));
        }
    }

    @Test
    void thresholdIsFivePercentOfMaxHealthAndIncludesBoundary() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        float threshold = WinefoxHighDamageGuard.threshold(600.0D);
        assertEquals(30.0F, threshold);
        assertEquals(50.0F, WinefoxHighDamageGuard.threshold(1_000.0D));
        guard.recordEffectiveHit(29.0F, threshold, 0L);
        assertFalse(guard.pending());
        guard.recordEffectiveHit(30.0F, threshold, 0L);
        assertTrue(guard.pending());
        guard.recordEffectiveHit(20.0F, threshold, 0L);
        assertTrue(guard.pending());
    }

    @Test
    void guardExpiresAfterLifetime() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        guard.recordEffectiveHit(100.0F, WinefoxHighDamageGuard.threshold(600.0D), 0L);
        assertEquals(0.7D, guard.multiplier(WinefoxHighDamageGuard.LIFETIME_TICKS));
        assertEquals(1.0D, guard.multiplier(WinefoxHighDamageGuard.LIFETIME_TICKS + 1L));
        assertFalse(guard.pending());
    }

    @Test
    void pendingStateCanBeRestoredAndReset() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        guard.setPending(true, 0L);
        assertEquals(0.7D, guard.multiplier(0L));
        guard.setPending(false, 0L);
        assertEquals(1.0D, guard.multiplier(0L));
    }
}
