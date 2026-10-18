package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import net.minecraft.world.entity.LivingEntity;

/** 给场景测试用的同包访问口：星之魔女的承伤换算、动态限伤、高伤保护和剑系连段状态机都是包内可见。 */
public final class WinefoxTestAccess {
    private WinefoxTestAccess() {
    }

    public static boolean beginMobCombat(MagicalWinefoxBossEntity boss, LivingEntity attacker) {
        return boss.beginMobCombat(attacker);
    }

    /** 以管理身份补满权威生命。 */
    public static void fillHealth(MagicalWinefoxBossEntity boss) {
        WinefoxBossHealthController.withCause(boss, WinefoxBossHealthController.Cause.ADMIN,
                () -> boss.setHealth(boss.maidspell$bossMaxHealth()));
    }

    /** 以伤害身份把权威生命往下写 amount，返回实际掉的血；被受击间隔挡下时为 0。 */
    public static float hit(MagicalWinefoxBossEntity boss, float amount) {
        float before = boss.maidspell$authoritativeHealth();
        WinefoxBossHealthController.withCause(boss, WinefoxBossHealthController.Cause.DAMAGE,
                () -> boss.setHealth(before - amount));
        return before - boss.maidspell$authoritativeHealth();
    }

    public static double adaptiveTargetDps(MagicalWinefoxBossEntity boss) {
        return boss.maidspell$adaptiveTargetDps();
    }

    public static double adaptiveActivationRatio(MagicalWinefoxBossEntity boss) {
        return boss.maidspell$adaptiveActivationRatio();
    }

    public static double adaptiveFinalBudgetMultiplier(MagicalWinefoxBossEntity boss) {
        return boss.maidspell$adaptiveFinalBudgetMultiplier();
    }

    public static int adaptiveWindowTicks(MagicalWinefoxBossEntity boss) {
        return boss.maidspell$adaptiveWindowTicks();
    }

    public static float damageBeforeCap(float previous, float requested) {
        return WinefoxBossHealthController.damageBeforeCap(previous, requested);
    }

    public static float reduceAndCapDamage(float damage, double multiplier, float cap) {
        return WinefoxBossHealthController.reduceAndCapDamage(damage, multiplier, cap);
    }

    public static float highDamageThreshold(double maxHealth) {
        return WinefoxHighDamageGuard.threshold(maxHealth);
    }

    public static float enforceMinimumDamage(float previous, float health, float cap) {
        return WinefoxBossHealthController.enforceMinimumDamage(previous, health, cap);
    }

    public static final int HIGH_DAMAGE_GUARD_LIFETIME_TICKS = WinefoxHighDamageGuard.LIFETIME_TICKS;

    public static final class HighDamageGuard {
        private final WinefoxHighDamageGuard guard = new WinefoxHighDamageGuard();

        public boolean pending() {
            return guard.pending();
        }

        public void setPending(boolean pending, long now) {
            guard.setPending(pending, now);
        }

        public double multiplier(long now) {
            return guard.multiplier(now);
        }

        public void recordEffectiveHit(float incoming, float threshold, long now) {
            guard.recordEffectiveHit(incoming, threshold, now);
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
