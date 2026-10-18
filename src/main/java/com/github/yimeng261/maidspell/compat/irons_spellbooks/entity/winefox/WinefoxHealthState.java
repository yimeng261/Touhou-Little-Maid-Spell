package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import java.util.concurrent.atomic.AtomicReference;

final class WinefoxHealthState {
    private final AtomicReference<Float> health = new AtomicReference<>();
    private final AtomicReference<Float> maxHealth = new AtomicReference<>();

    float getHealth(float fallback) {
        Float value = this.health.get();
        return value != null ? value : fallback;
    }

    void setHealth(float value) {
        this.health.set(value);
    }

    float getMaxHealth(float fallback) {
        Float value = this.maxHealth.get();
        return value != null ? value : fallback;
    }

    void setMaxHealth(float value) {
        this.maxHealth.set(value);
    }
}
