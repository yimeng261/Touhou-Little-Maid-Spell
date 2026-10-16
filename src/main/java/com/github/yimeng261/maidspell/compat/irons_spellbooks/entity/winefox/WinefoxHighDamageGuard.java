package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/** A high resolved hit grants a single, non-stacking reduction to the next effective hit. */
final class WinefoxHighDamageGuard {
    private boolean pending;

    boolean pending() {
        return this.pending;
    }

    void setPending(boolean pending) {
        this.pending = pending;
    }

    double multiplier() {
        return this.pending ? 0.7D : 1.0D;
    }

    static float threshold(double defaultMaxHealth) {
        return (float) (defaultMaxHealth * 0.05D);
    }

    // Called only after damage survives all gates and really lowers health.
    void recordEffectiveHit(float incoming, float threshold) {
        this.pending = Float.isFinite(incoming) && Float.isFinite(threshold)
                && threshold > 0.0F && incoming >= threshold;
    }
}
