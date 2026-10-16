package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinefoxSwordComboTest {
    @Test
    void followsSwordArrayPauseFlashTeleportAndSlashInOrder() {
        WinefoxSwordCombo combo = new WinefoxSwordCombo();
        combo.start(100);
        assertEquals(WinefoxSwordCombo.Step.PAUSE, combo.step(119));
        assertEquals(WinefoxSwordCombo.Step.FLASH, combo.step(120));
        combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        assertEquals(WinefoxSwordCombo.Step.FLASH_CASTING, combo.step(121));
        combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        assertEquals(WinefoxSwordCombo.Step.TELEPORT, combo.step(122));
        combo.teleported();
        assertEquals(WinefoxSwordCombo.Step.SLASH, combo.step(123));
        combo.castStarted(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        assertEquals(WinefoxSwordCombo.Step.SLASH_CASTING, combo.step(124));
        combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        assertFalse(combo.isActive());
    }

    @Test
    void unrelatedSpellReleasesCannotSkipThePauseOrFlash() {
        WinefoxSwordCombo combo = new WinefoxSwordCombo();
        combo.start(0);
        combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        combo.teleported();
        assertEquals(WinefoxSwordCombo.Step.PAUSE, combo.step(19));
        assertEquals(WinefoxSwordCombo.Step.FLASH, combo.step(20));
        combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        assertEquals(WinefoxSwordCombo.Step.FLASH_CASTING, combo.step(100));
    }

    @Test
    void interruptionClearsEveryStepAndAllowsANewCombo() {
        for (WinefoxSwordCombo.Step interrupted : WinefoxSwordCombo.Step.values()) {
            WinefoxSwordCombo combo = new WinefoxSwordCombo();
            combo.start(0);
            if (interrupted.ordinal() >= WinefoxSwordCombo.Step.FLASH.ordinal()) combo.step(20);
            if (interrupted.ordinal() >= WinefoxSwordCombo.Step.FLASH_CASTING.ordinal()) {
                combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            }
            if (interrupted.ordinal() >= WinefoxSwordCombo.Step.TELEPORT.ordinal()) {
                combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            }
            if (interrupted.ordinal() >= WinefoxSwordCombo.Step.SLASH.ordinal()) combo.teleported();
            if (interrupted == WinefoxSwordCombo.Step.SLASH_CASTING) {
                combo.castStarted(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
            }
            combo.cancel();
            combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
            assertFalse(combo.isActive(), interrupted.name());
            combo.start(200);
            assertTrue(combo.isActive());
            assertEquals(WinefoxSwordCombo.Step.PAUSE, combo.step(219));
            assertEquals(WinefoxSwordCombo.Step.FLASH, combo.step(220));
        }
    }
}
