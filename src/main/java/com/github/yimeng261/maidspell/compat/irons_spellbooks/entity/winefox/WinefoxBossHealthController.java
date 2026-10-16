package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.api.IBossDamageClamp;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.utils.BossLifecycleAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 酒狐生命值的统一写入层，集中执行单次伤害上限和有效受击间隔。
 * 恢复、读档、管理操作与战败归零不受伤害限制。
 */
public final class WinefoxBossHealthController {
    public enum Cause {
        DAMAGE, HEAL, LOAD, RESET, SCRIPTED_TRANSITION, ADMIN, EXTERNAL_UNKNOWN
    }

    private static final ThreadLocal<Deque<WriteContext>> CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    private WinefoxBossHealthController() {
    }

    public static void withCause(MagicalWinefoxBossEntity boss, Cause cause, Runnable action) {
        Deque<WriteContext> stack = CONTEXT.get();
        stack.push(new WriteContext(boss, cause));
        try {
            action.run();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                CONTEXT.remove();
            }
        }
    }

    public static Cause currentCause(MagicalWinefoxBossEntity boss) {
        for (WriteContext context : CONTEXT.get()) {
            if (context.boss == boss) {
                return context.cause;
            }
        }
        return Cause.EXTERNAL_UNKNOWN;
    }

    public static float sanitize(float value, float max) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0F, max) : max;
    }

    /** 单次写入允许的最大下降量；返回 {@link IBossDamageClamp#NO_DAMAGE_CAP} 表示不限伤。 */
    public static float damageCap(MagicalWinefoxBossEntity boss) {
        double ratio = boss.maidspell$hitDamageCapRatio();
        if (ratio <= 0.0D) {
            return IBossDamageClamp.NO_DAMAGE_CAP;
        }
        return (float) (boss.maidspell$bossMaxHealth() * ratio);
    }

    /**
     * Re-enter the authoritative health path for a write that arrived through
     * SynchedEntityData rather than through {@code LivingEntity#setHealth}.
     *
     * <p>The expected-write marker handles the second raw mirror write made by
     * a caller that already invoked {@code setHealth}; an unrelated write is
     * treated as unattributed damage and receives the normal cap and lifecycle
     * handling.</p>
     */
    public static void handleExternalHealthWrite(MagicalWinefoxBossEntity boss, float requested) {
        if (boss.level().isClientSide) {
            return;
        }
        if (boss.maidspell$consumeExpectedHealthWrite()) {
            syncMirrors(boss);
            return;
        }
        float previous = boss.maidspell$authoritativeHealth();
        withCause(boss, Cause.EXTERNAL_UNKNOWN, () -> write(boss, requested));
        if (boss.maidspell$authoritativeHealth() == previous) {
            syncMirrors(boss);
        }
    }

    /** Restore the authoritative maximum-health mirror after an external write. */
    public static void handleExternalMaxHealthWrite(MagicalWinefoxBossEntity boss) {
        if (!boss.level().isClientSide) {
            boss.maidspell$syncMaxHealthMirror();
        }
    }

    private static void syncMirrors(MagicalWinefoxBossEntity boss) {
        BossLifecycleAccess.withDataWrite(boss, () -> {
            boss.getEntityData().set(MagicalWinefoxBossEntity.BOSS_HEALTH, boss.maidspell$authoritativeHealth());
            boss.getEntityData().set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), 1.0F);
        });
    }

    /**
     * 扣除一次伤害。倍率在这里施加；上限与间隔在 {@link #write} 里执行。
     *
     * <p>调用方负责用 {@link #withCause} 把调用包进 {@code DAMAGE} 上下文。
     *
     * @param maidDamage 是否出自女仆或其召唤物，决定是否叠加女仆减伤
     * @return 这一击是否真的让生命下降
     */
    public static boolean applyDamage(MagicalWinefoxBossEntity boss, DamageSource source,
                                      float amount, boolean maidDamage) {
        if (boss.level().isClientSide || !Float.isFinite(amount) || amount <= 0.0F) {
            return false;
        }
        float previous = boss.maidspell$authoritativeHealth();
        if (previous <= 0.0F) {
            return false;
        }
        float adjusted = applyDamageMultipliers(boss, amount, maidDamage);
        if (Config.winefoxDamageDebugLogging) {
            Global.LOGGER.info("[WinefoxDamage] stage=input boss={} tick={} state={} phaseTwo={} "
                            + "source={} attacker={} direct={} maid={} raw={} baseAdjusted={} "
                            + "globalMultiplier={} maidMultiplier={} phaseMultiplier={} health={}",
                    boss.getUUID(), boss.tickCount, boss.maidspell$encounterState(), boss.isPhaseTwo(),
                    source.getMsgId(), source.getEntity() == null ? "none" : source.getEntity().getUUID(),
                    source.getDirectEntity() == null ? "none" : source.getDirectEntity().getUUID(),
                    maidDamage, amount, adjusted, boss.maidspell$damageMultiplier(),
                    maidDamage ? boss.maidspell$maidDamageMultiplier() : 1.0D,
                    boss.isPhaseTwo() && boss.isBattleActive() ? boss.maidspell$phaseTwoDamageMultiplier() : 1.0D,
                    previous);
        }
        if (adjusted <= 0.0F) {
            logBlockedHit(boss, source, amount, "zero_base_damage");
            return false;
        }
        // 原版结算链负责护甲、抗性、吸收与事件；Mixin 将链内读血改为权威值，
        // setHealth 仍回到 write()，上限与间隔在那里执行。
        boss.maidspell$runVanillaDamagePipeline(source, adjusted);
        if (Config.winefoxDamageDebugLogging) {
            Global.LOGGER.info("[WinefoxDamage] stage=result boss={} tick={} source={} healthBefore={} "
                            + "healthAfter={} netHealthLoss={} outcome={}",
                    boss.getUUID(), boss.tickCount, source.getMsgId(), previous,
                    boss.maidspell$authoritativeHealth(), previous - boss.maidspell$authoritativeHealth(),
                    boss.maidspell$authoritativeHealth() < previous ? "damaged" : "no_health_loss");
        }
        return boss.maidspell$authoritativeHealth() < previous;
    }

    /** 施加对 Boss 的伤害倍率：全局一层，女仆与二阶段各自再叠一层。 */
    public static float applyDamageMultipliers(MagicalWinefoxBossEntity boss, float amount, boolean maidDamage) {
        float adjusted = amount * (float) boss.maidspell$damageMultiplier();
        if (maidDamage) {
            adjusted *= (float) boss.maidspell$maidDamageMultiplier();
        }
        if (boss.isPhaseTwo() && boss.isBattleActive()) {
            adjusted *= (float) boss.maidspell$phaseTwoDamageMultiplier();
        }
        return adjusted;
    }

    // Read the requested decrease before sanitize() discards overkill damage.
    static float damageBeforeCap(float previous, float requested) {
        return Float.isFinite(requested)
                ? (float) Math.min(Float.MAX_VALUE, Math.max(0.0D, (double) previous - requested))
                : 0.0F;
    }

    static float reduceAndCapDamage(float damage, double multiplier, float cap) {
        return (float) Math.min(cap, Math.max(0.0D, damage * multiplier));
    }

    private static double adaptivePreCapMultiplier(MagicalWinefoxBossEntity boss) {
        if (!boss.maidspell$adaptiveDamageEnabled()) {
            return 1.0D;
        }
        double target = boss.maidspell$adaptiveTargetDps();
        if (target <= 0.0D) {
            return 1.0D;
        }
        double ratio = boss.maidspell$adaptiveAttemptedDps() / target;
        double activation = boss.maidspell$adaptiveActivationRatio();
        double saturation = Math.max(activation, boss.maidspell$adaptiveSaturationRatio());
        if (ratio <= activation) {
            return 1.0D;
        }
        double progress = Mth.clamp((ratio - activation) / (saturation - activation), 0.0D, 1.0D);
        return Mth.lerp(progress, boss.maidspell$adaptiveStartMultiplier(),
                boss.maidspell$adaptiveMinMultiplier());
    }

    private static double adaptivePostCapMultiplier(MagicalWinefoxBossEntity boss) {
        if (!boss.maidspell$adaptiveDamageEnabled()) {
            return 1.0D;
        }
        double target = boss.maidspell$adaptiveTargetDps();
        if (target <= 0.0D) {
            return 1.0D;
        }
        double ratio = boss.maidspell$adaptiveEffectiveDps() / target;
        double activation = boss.maidspell$adaptiveActivationRatio();
        double saturation = Math.max(activation, boss.maidspell$adaptiveSaturationRatio());
        if (ratio <= activation) {
            return 1.0D;
        }
        double progress = Mth.clamp((ratio - activation) / (saturation - activation), 0.0D, 1.0D);
        return Mth.lerp(progress, boss.maidspell$adaptiveStartMultiplier(),
                boss.maidspell$adaptiveMinMultiplier());
    }

    /** Final damage budget after the ordinary per-hit cap, expressed in damage per hit. */
    private static float adaptiveFinalDamageBudget(MagicalWinefoxBossEntity boss) {
        if (!boss.maidspell$adaptiveDamageEnabled() || !boss.maidspell$isAdaptiveDamageActive()) {
            return IBossDamageClamp.NO_DAMAGE_CAP;
        }
        double targetDps = boss.maidspell$adaptiveTargetDps();
        double intervalSeconds = Math.max(1, boss.maidspell$hitIntervalTicks()) / 20.0D;
        return (float) Math.max(0.0D, targetDps * intervalSeconds
                * boss.maidspell$adaptiveFinalBudgetMultiplier());
    }

    /**
     * 权威生命的所有写入都走这里。
     *
     * <p>对下降类写入执行单次上限与受击间隔；命中间隔被挡时直接返回，不改动任何状态，
     * 由调用方据此判断"这一击没生效"。
     */
    public static void write(MagicalWinefoxBossEntity boss, float requested) {
        if (boss.level().isClientSide) {
            return;
        }
        Cause cause = boss.maidspell$isLoadingLifecycle() ? Cause.LOAD : currentCause(boss);
        WinefoxEncounterState state = boss.maidspell$encounterState();
        if (cause == Cause.HEAL && (state == WinefoxEncounterState.DEFEATED
                || state == WinefoxEncounterState.RETURNING)) {
            return;
        }
        float previous = boss.maidspell$authoritativeHealth();
        float health = sanitize(requested, boss.maidspell$bossMaxHealth());
        if (cause == Cause.HEAL && health < previous) {
            return;
        }

        if (health < previous) {
            if (isCappedCause(cause)) {
                if (cause == Cause.EXTERNAL_UNKNOWN
                        && (state != WinefoxEncounterState.COMBAT || boss.isTransitioning())) {
                    logBlockedWrite(boss, cause, previous, requested, "inactive_or_transitioning");
                    return;
                }
                if (isWithinHitInterval(boss)) {
                    logBlockedWrite(boss, cause, previous, requested, "hit_interval");
                    return;
                }
                float incomingDamage = damageBeforeCap(previous, requested);
                double multiplier = 1.0D;
                double attemptedDps = Config.winefoxDamageDebugLogging ? boss.maidspell$adaptiveAttemptedDps() : 0.0D;
                double effectiveDps = Config.winefoxDamageDebugLogging ? boss.maidspell$adaptiveEffectiveDps() : 0.0D;
                double postMultiplier = 1.0D;
                float budget = IBossDamageClamp.NO_DAMAGE_CAP;
                boolean budgetApplied = false;
                if (boss.maidspell$isAdaptiveDamageActive() && boss.maidspell$adaptiveDamageEnabled()) {
                    // Both vanilla damage and additional direct health writes converge
                    // here. Decide using prior history, then record the full
                    // resolved input immediately before reduction and the cap.
                    multiplier = adaptivePreCapMultiplier(boss);
                    boss.maidspell$recordAdaptiveAttempt(incomingDamage);
                }
                float cap = damageCap(boss);
                float reducedDamage = reduceAndCapDamage(incomingDamage, multiplier, cap);
                health = Math.max(0.0F, previous - reducedDamage);
                float postCapDamage = previous - health;
                double highDamageGuardMultiplier = 1.0D;
                float highDamageThreshold = WinefoxHighDamageGuard.threshold(Config.winefoxMaxHealth);
                boolean highDamageGuardActive = boss.maidspell$isAdaptiveDamageActive()
                        && boss.maidspell$adaptiveDamageEnabled();
                if (postCapDamage > 0.0F && boss.maidspell$isAdaptiveDamageActive()) {
                    double target = boss.maidspell$adaptiveTargetDps();
                    double effectiveRatio = target <= 0.0D ? 0.0D
                            : boss.maidspell$adaptiveEffectiveDps() / target;
                    budget = adaptiveFinalDamageBudget(boss);
                    float allowed = postCapDamage;
                    if (effectiveRatio > boss.maidspell$adaptiveActivationRatio()) {
                        postMultiplier = adaptivePostCapMultiplier(boss);
                        allowed *= (float) postMultiplier;
                        if (budget != IBossDamageClamp.NO_DAMAGE_CAP) {
                            budgetApplied = true;
                            allowed = Math.min(allowed, budget);
                        }
                    }
                    health = previous - Math.max(0.0F, allowed);
                }
                if (highDamageGuardActive) {
                    WinefoxHighDamageGuard guard = boss.maidspell$highDamageGuard();
                    highDamageGuardMultiplier = guard.multiplier();
                    health = Math.max(0.0F, previous
                            - (float) ((previous - health) * highDamageGuardMultiplier));
                    if (health < previous) {
                        guard.recordEffectiveHit(incomingDamage, highDamageThreshold);
                    }
                }
                if (Config.winefoxDamageDebugLogging) {
                    Global.LOGGER.info("[WinefoxDamage] stage=resolved boss={} tick={} cause={} phaseTwo={} "
                                    + "adaptiveEnabled={} requestedHealth={} incoming={} attemptedDpsBefore={} "
                                    + "effectiveDpsBefore={} targetDps={} activationRatio={} preMultiplier={} "
                                    + "afterPreReduction={} hitCap={} afterCap={} postMultiplier={} "
                                    + "budget={} budgetApplied={} highDamageThreshold={} highDamageGuardMultiplier={} "
                                    + "highDamageGuardPending={} healthBefore={} healthAfter={} actualDamage={}",
                            boss.getUUID(), boss.tickCount, cause, boss.isPhaseTwo(),
                            boss.maidspell$adaptiveDamageEnabled(), requested, incomingDamage, attemptedDps,
                            effectiveDps, boss.maidspell$adaptiveTargetDps(), boss.maidspell$adaptiveActivationRatio(),
                            multiplier, incomingDamage * (double) multiplier,
                            cap == IBossDamageClamp.NO_DAMAGE_CAP ? "unlimited" : cap,
                            postCapDamage, postMultiplier,
                            budget == IBossDamageClamp.NO_DAMAGE_CAP ? "unlimited" : budget,
                            budgetApplied, highDamageThreshold, highDamageGuardMultiplier,
                            boss.maidspell$highDamageGuard().pending(),
                            previous, health, previous - health);
                }
            }
        }

        restore(boss, health, previous, cause);
        if (health < previous && isCappedCause(cause) && boss.maidspell$isAdaptiveDamageActive()) {
            boss.maidspell$recordAdaptiveEffectiveDamage(previous - health);
        }
    }

    /** 只有伤害类写入受上限与间隔约束；恢复类写入必须能自由写回。 */
    private static boolean isCappedCause(Cause cause) {
        return cause == Cause.DAMAGE || cause == Cause.EXTERNAL_UNKNOWN;
    }

    /**
     * 这一击会不会被受击间隔挡下 —— 给 {@code hurt()} 在跑原版结算链<b>之前</b>问一句。
     *
     * <p>不能等落到 {@link #write} 才挡：原版那一段（{@code super.hurt}）会重置 {@code hurtTime} /
     * {@code hurtDuration}、广播受击动画并结算击退，写血被挡下时这些副作用早就发生过了。本类的契约
     * 是"被挡下的那一击不产生任何副作用"，只有提前判定才落实得了。
     */
    public static boolean wouldBlockHitInterval(MagicalWinefoxBossEntity boss) {
        return isWithinHitInterval(boss);
    }

    static void logBlockedHit(MagicalWinefoxBossEntity boss, DamageSource source, float amount, String reason) {
        if (Config.winefoxDamageDebugLogging) {
            Global.LOGGER.info("[WinefoxDamage] stage=blocked_input boss={} tick={} source={} attacker={} "
                            + "raw={} health={} reason={} hitInterval={} lastHitTick={}",
                    boss.getUUID(), boss.tickCount, source.getMsgId(),
                    source.getEntity() == null ? "none" : source.getEntity().getUUID(),
                    amount, boss.maidspell$authoritativeHealth(), reason, boss.maidspell$hitIntervalTicks(),
                    boss.maidspell$lastEffectiveHitTick());
        }
    }

    private static void logBlockedWrite(MagicalWinefoxBossEntity boss, Cause cause, float previous,
                                        float requested, String reason) {
        if (Config.winefoxDamageDebugLogging) {
            Global.LOGGER.info("[WinefoxDamage] stage=blocked_write boss={} tick={} cause={} state={} "
                            + "requestedHealth={} incoming={} health={} reason={} hitInterval={} lastHitTick={}",
                    boss.getUUID(), boss.tickCount, cause, boss.maidspell$encounterState(), requested,
                    damageBeforeCap(previous, requested), previous, reason, boss.maidspell$hitIntervalTicks(),
                    boss.maidspell$lastEffectiveHitTick());
        }
    }

    private static boolean isWithinHitInterval(MagicalWinefoxBossEntity boss) {
        int interval = boss.maidspell$hitIntervalTicks();
        if (interval <= 0) {
            return false;
        }
        long last = boss.maidspell$lastEffectiveHitTick();
        return last != Long.MIN_VALUE && boss.tickCount - last < interval;
    }

    private static void restore(MagicalWinefoxBossEntity boss, float health,
                                float previous, Cause cause) {
        boss.maidspell$setAuthoritativeHealth(health);
        if (health < previous && isCappedCause(cause)) {
            boss.maidspell$markEffectiveHit();
        }
        BossLifecycleAccess.withDataWrite(boss, () -> {
            boss.getEntityData().set(MagicalWinefoxBossEntity.BOSS_HEALTH, health);
            boss.getEntityData().set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), 1.0F);
        });
        if (cause == Cause.EXTERNAL_UNKNOWN) {
            boss.maidspell$expectExternalHealthWrite();
        }
        if (cause == Cause.EXTERNAL_UNKNOWN && previous != health) {
            if (health < previous) {
                boss.maidspell$recordUnattributedDamage(previous - health);
            }
            if (health == 0.0F) {
                boss.maidspell$handleUnattributedDefeat();
            }
        }
    }

    private record WriteContext(MagicalWinefoxBossEntity boss, Cause cause) {
    }
}
