package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/** 一次高额结算命中会让后续有限时间内的下一次有效命中获得一次不叠加的减伤。 */
final class WinefoxHighDamageGuard {
    static final int LIFETIME_TICKS = 100;

    private boolean pending;
    private long expiresAt = Long.MIN_VALUE;

    boolean pending() {
        return this.pending;
    }

    void setPending(boolean pending, long now) {
        this.pending = pending;
        this.expiresAt = pending ? now + LIFETIME_TICKS : Long.MIN_VALUE;
    }

    double multiplier(long now) {
        if (this.pending && now > this.expiresAt) {
            this.pending = false;
            this.expiresAt = Long.MIN_VALUE;
        }
        return this.pending ? 0.7D : 1.0D;
    }

    static float threshold(double maxHealth) {
        return (float) (maxHealth * 0.05D);
    }

    // 仅在伤害通过所有闸门、真正降低了血量之后调用。
    void recordEffectiveHit(float incoming, float threshold, long now) {
        if (!Float.isFinite(incoming) || !Float.isFinite(threshold)
                || threshold <= 0.0F || incoming < threshold) {
            return;
        }
        this.pending = true;
        this.expiresAt = now + LIFETIME_TICKS;
    }
}
