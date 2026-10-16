package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinefoxDamageReductionTest {
    @Test
    void reductionPrecedesTheSingleHitCap() {
        float damage = WinefoxBossHealthController.damageBeforeCap(300.0F, 200.0F);
        // Reducing 100 to 50 before a 48 cap differs from reducing a capped 48 to 24.
        assertEquals(48.0F, WinefoxBossHealthController.reduceAndCapDamage(damage, 0.5D, 48.0F));
    }

    @Test
    void additionalDirectHealthWriteIsIncluded() {
        float damage = WinefoxBossHealthController.damageBeforeCap(250.0F, 190.0F);
        assertEquals(60.0F, damage);
        assertEquals(30.0F, WinefoxBossHealthController.reduceAndCapDamage(damage, 0.5D, 48.0F));
    }

    @Test
    void overkillPressureIsNotClippedToRemainingHealth() {
        float damage = WinefoxBossHealthController.damageBeforeCap(300.0F, 300.0F - 1_000_000_000.0F);
        assertEquals(1_000_000_000.0F, damage, 128.0F);
        assertEquals(48.0F, WinefoxBossHealthController.reduceAndCapDamage(damage, 0.05D, 48.0F));
    }

    @Test
    void extremeFiniteWritesStayFinite() {
        float damage = WinefoxBossHealthController.damageBeforeCap(Float.MAX_VALUE, -Float.MAX_VALUE);
        assertTrue(Float.isFinite(damage));
        assertEquals(Float.MAX_VALUE, damage);
    }

    @Test
    void healingAndInvalidWritesDoNotBecomeDamage() {
        assertEquals(0.0F, WinefoxBossHealthController.damageBeforeCap(300.0F, 320.0F));
        assertEquals(0.0F, WinefoxBossHealthController.damageBeforeCap(300.0F, Float.NaN));
        assertEquals(0.0F, WinefoxBossHealthController.damageBeforeCap(300.0F, Float.NEGATIVE_INFINITY));
    }
}
