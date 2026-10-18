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

    @Test
    void minimumDamageClearsTinyResidualAfterAllMitigation() {
        float health = WinefoxBossHealthController.enforceMinimumDamage(
                0.0140709765F, 0.0135784923F, 48.0F);
        assertEquals(0.0F, health);
    }

    @Test
    void minimumDamageAppliesAfterTheGuard() {
        float health = WinefoxBossHealthController.enforceMinimumDamage(1.0F, 0.9995F, 48.0F);
        assertEquals(0.9F, health, 1.0E-6F);
    }

    @Test
    void minimumDamageDoesNotExceedThePerHitCap() {
        float health = WinefoxBossHealthController.enforceMinimumDamage(0.2F, 0.199F, 0.05F);
        assertEquals(0.15F, health, 1.0E-6F);
    }

    @Test
    void zeroedDamageStillCountsAsAHitWhenTheWriteWasDamage() {
        float health = WinefoxBossHealthController.enforceMinimumDamage(0.2F, 0.2F, 48.0F);
        assertEquals(0.1F, health, 1.0E-6F);
    }

    @Test
    void tinyZeroedDamageSnapsToZero() {
        assertEquals(0.0F, WinefoxBossHealthController.enforceMinimumDamage(0.05F, 0.05F, 48.0F));
    }

    @Test
    void minimumDamageDoesNotTurnHealingIntoDamage() {
        assertEquals(0.2F, WinefoxBossHealthController.enforceMinimumDamage(0.1F, 0.2F, 48.0F));
    }

    @Test
    void lethalHitStaysAtZero() {
        assertEquals(0.0F, WinefoxBossHealthController.enforceMinimumDamage(0.2F, 0.0F, 48.0F));
    }

    @Test
    void unlimitedCapUsesTheDefaultMinimum() {
        float health = WinefoxBossHealthController.enforceMinimumDamage(
                1.0F, 0.999F, com.github.yimeng261.maidspell.api.IBossDamageClamp.NO_DAMAGE_CAP);
        assertEquals(0.9F, health, 1.0E-6F);
    }
}
