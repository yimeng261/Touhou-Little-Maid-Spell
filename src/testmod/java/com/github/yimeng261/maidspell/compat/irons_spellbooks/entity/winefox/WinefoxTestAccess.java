package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/** 给场景测试用的同包访问口：星之魔女的承伤换算、高伤保护和剑系连段状态机都是包内可见。 */
public final class WinefoxTestAccess {
    private WinefoxTestAccess() {
    }

    public static float damageBeforeCap(float previous, float requested) {
        return WinefoxBossHealthController.damageBeforeCap(previous, requested);
    }

    public static float reduceAndCapDamage(float damage, double multiplier, float cap) {
        return WinefoxBossHealthController.reduceAndCapDamage(damage, multiplier, cap);
    }

    public static float highDamageThreshold(double defaultMaxHealth) {
        return WinefoxHighDamageGuard.threshold(defaultMaxHealth);
    }

    public static final class HighDamageGuard {
        private final WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();

        public boolean pending() {
            return guard.pending();
        }

        public void setPending(boolean pending) {
            guard.setPending(pending);
        }

        public double multiplier() {
            return guard.multiplier();
        }

        public void recordEffectiveHit(float incoming, float threshold) {
            guard.recordEffectiveHit(incoming, threshold);
        }
    }

    /** 连段步骤以枚举名表示。 */
    public static final class SwordCombo {
        private final WinefoxSwordCombo combo = new WinefoxSwordCombo();

        public static String[] stepNames() {
            WinefoxSwordCombo.Step[] steps = WinefoxSwordCombo.Step.values();
            String[] names = new String[steps.length];
            for (int i = 0; i < steps.length; i++) {
                names[i] = steps[i].name();
            }
            return names;
        }

        public void start(int tick) {
            combo.start(tick);
        }

        public boolean isActive() {
            return combo.isActive();
        }

        public String step(int tick) {
            return combo.step(tick).name();
        }

        public void castStarted(WinefoxBossSpellAction action) {
            combo.castStarted(action);
        }

        public void spellReleased(WinefoxBossSpellAction action) {
            combo.spellReleased(action);
        }

        public void teleported() {
            combo.teleported();
        }

        public void cancel() {
            combo.cancel();
        }
    }
}
