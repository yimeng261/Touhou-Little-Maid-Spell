package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/** 一次高额结算命中会让下一次有效命中获得一次不叠加的减伤。 */
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

    // 仅在伤害通过所有闸门、真正降低了血量之后调用。
    void recordEffectiveHit(float incoming, float threshold) {
        this.pending = Float.isFinite(incoming) && Float.isFinite(threshold)
                && threshold > 0.0F && incoming >= threshold;
    }
}
