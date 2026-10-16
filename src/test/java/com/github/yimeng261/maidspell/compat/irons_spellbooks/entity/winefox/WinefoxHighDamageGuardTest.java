package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WinefoxHighDamageGuardTest {
    @Test
    void highHitProtectsOnlyTheNextEffectiveHit() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        assertEquals(1.0D, guard.multiplier());
        guard.recordEffectiveHit(100.0F, WinefoxHighDamageGuard.threshold(600.0D));
        assertEquals(0.7D, guard.multiplier());
        assertEquals(33.6D, 48.0D * guard.multiplier(), 0.0001D);
        guard.recordEffectiveHit(20.0F, WinefoxHighDamageGuard.threshold(600.0D));
        assertFalse(guard.pending());
        assertEquals(1.0D, guard.multiplier());
    }

    @Test
    void repeatedHighHitsRefreshWithoutStacking() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        for (int i = 0; i < 5; i++) {
            guard.recordEffectiveHit(1_000_000_000.0F, WinefoxHighDamageGuard.threshold(600.0D));
            assertEquals(0.7D, guard.multiplier());
        }
    }

    @Test
    void thresholdIsFivePercentOfDefaultHealthAndIncludesBoundary() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        float threshold = WinefoxHighDamageGuard.threshold(600.0D);
        assertEquals(30.0F, threshold);
        assertEquals(50.0F, WinefoxHighDamageGuard.threshold(1_000.0D));
        guard.recordEffectiveHit(29.0F, threshold);
        assertFalse(guard.pending());
        guard.recordEffectiveHit(30.0F, threshold);
        assertTrue(guard.pending());
        guard.recordEffectiveHit(20.0F, threshold);
        assertFalse(guard.pending());
    }

    @Test
    void pendingStateCanBeRestoredAndReset() {
        WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();
        guard.setPending(true);
        assertEquals(0.7D, guard.multiplier());
        guard.setPending(false);
        assertEquals(1.0D, guard.multiplier());
    }
}
