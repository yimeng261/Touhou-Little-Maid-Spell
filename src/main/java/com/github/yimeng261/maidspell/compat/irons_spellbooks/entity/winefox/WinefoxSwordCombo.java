package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/** Follow-ups advance on actual spell releases, so cancelled casts cannot skip steps. */
final class WinefoxSwordCombo {
    static final int PAUSE_TICKS = 20;

    enum Step { NONE, PAUSE, FLASH, FLASH_CASTING, TELEPORT, SLASH, SLASH_CASTING }

    private Step step = Step.NONE;
    private int pauseEndTick;

    void start(int tick) {
        this.step = Step.PAUSE;
        this.pauseEndTick = tick + PAUSE_TICKS;
    }

    boolean isActive() {
        return this.step != Step.NONE;
    }

    Step step(int tick) {
        if (this.step == Step.PAUSE && tick >= this.pauseEndTick) this.step = Step.FLASH;
        return this.step;
    }

    void castStarted(WinefoxBossSpellAction action) {
        if (this.step == Step.FLASH && action == WinefoxBossSpellAction.MODIFIED_TELEPORT) {
            this.step = Step.FLASH_CASTING;
        } else if (this.step == Step.SLASH && action == WinefoxBossSpellAction.STAR_SHADOW_STRIKE) {
            this.step = Step.SLASH_CASTING;
        }
    }

    void spellReleased(WinefoxBossSpellAction action) {
        if (this.step == Step.FLASH_CASTING && action == WinefoxBossSpellAction.MODIFIED_TELEPORT) {
            this.step = Step.TELEPORT;
        } else if (this.step == Step.SLASH_CASTING && action == WinefoxBossSpellAction.STAR_SHADOW_STRIKE) {
            this.cancel();
        }
    }

    void teleported() {
        if (this.step == Step.TELEPORT) this.step = Step.SLASH;
    }

    void cancel() {
        this.step = Step.NONE;
    }
}
