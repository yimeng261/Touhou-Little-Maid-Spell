package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.IBossDamageClamp;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.utils.BossLifecycleAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 星之魔女权威生命的唯一写入层。
 *
 * <p><b>单次受击上限与受击间隔都在这里执行</b>，而不是在 {@code hurt()} 或某个 Mixin 里。
 * 原因只有一个：这里是所有会改变她生命的路径的汇合点 —— 原版伤害链、真伤回流、
 * 公开 {@code setHealth(...)}、第三方直写同步字段，最终都落到 {@link #write}。
 * 放在上游任何一层都只是"多一道可以被绕过的检查"。
 *
 * <p>两条规则的具体语义：
 *
 * <ul>
 *   <li><b>单次上限</b>：一次写入最多让生命下降配置的比例（默认 8%）。它衡量的是
 *       {@code write} 收到的那个新值相对当前值的差值，因此对任何来源一视同仁。</li>
 *   <li><b>受击间隔</b>：两次<b>真正生效</b>的下降之间至少间隔配置的 tick 数（默认 6 = 0.3 秒）。
 *       被挡下的那一次不产生任何副作用（不记伤害归属、不结仇、不推进同 tick 的后续写入），
 *       已生效的那一次才更新时间戳。这与原版 {@code invulnerableTime} 无关，二者互不读写。</li>
 * </ul>
 *
 * <p>恢复类写入（{@code RESET} / {@code LOAD} / {@code ADMIN} / 战败演出归零）不受这两条约束：
 * 它们是内部状态恢复，需要能写回满血；限制它们只会让归位和读档失效。
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
        double ratio = Config.winefoxHitDamageCapRatio;
        if (ratio <= 0.0D) {
            return IBossDamageClamp.NO_DAMAGE_CAP;
        }
        return (float) (boss.maidspell$bossMaxHealth() * ratio);
    }

    /**
     * 扣除一次伤害。调用方负责用 {@link #withCause} 把这次调用包进 {@code DAMAGE} 上下文。
     *
     * <p>倍率在这里施加；单次上限与受击间隔在 {@link #write} 里执行 —— 后者是所有写血路径的
     * 汇合点，因此不必也不应该在调用链上游重复一遍。
     *
     * @param maidDamage 这一击是否出自女仆或其召唤物，决定是否叠加女仆减伤
     * @return 这一击是否真的让生命下降
     */
    public static boolean applyDamage(MagicalWinefoxBossEntity boss, DamageSource source,
                                      float amount, boolean maidDamage) {
        if (boss.level().isClientSide || !Float.isFinite(amount) || amount <= 0.0F) {
            return false;
        }
        float previous = boss.getHealth();
        if (previous <= 0.0F) {
            return false;
        }
        float adjusted = applyDamageMultipliers(boss, amount, maidDamage);
        if (adjusted <= 0.0F) {
            return false;
        }
        // 原版结算链只用来跑护甲、抗性、吸收与事件；它读写的 getHealth/setHealth
        // 都是虚方法，因此实际落值必然回到 write()，上限与间隔在那里执行。
        boss.maidspell$runVanillaDamagePipeline(source, adjusted);
        boolean changed = boss.getHealth() < previous;
        if (changed) {
            boss.maidspell$markEffectiveHit();
        }
        return changed;
    }

    /** 施加对 Boss 的伤害倍率：全局一层，女仆与二阶段各自再叠一层。 */
    public static float applyDamageMultipliers(MagicalWinefoxBossEntity boss, float amount, boolean maidDamage) {
        float adjusted = amount * (float) Config.winefoxDamageMultiplier;
        if (maidDamage) {
            adjusted *= (float) Config.winefoxMaidDamageMultiplier;
        }
        if (boss.maidspell$isPlayerCombatPhaseTwo()) {
            adjusted *= (float) Config.winefoxPhaseTwoDamageMultiplier;
        }
        return adjusted;
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
        float previous = boss.getHealth();
        float health = sanitize(requested, boss.maidspell$bossMaxHealth());

        if (health < previous) {
            if (isCappedCause(cause)) {
                if (isWithinHitInterval(boss)) {
                    return;
                }
                float cap = damageCap(boss);
                if (cap != IBossDamageClamp.NO_DAMAGE_CAP) {
                    health = Math.max(previous - cap, health);
                }
            }
        }

        restore(boss, health, previous, cause);
    }

    /** 只有伤害类写入受上限与间隔约束；恢复类写入必须能自由写回。 */
    private static boolean isCappedCause(Cause cause) {
        return cause == Cause.DAMAGE || cause == Cause.EXTERNAL_UNKNOWN;
    }

    private static boolean isWithinHitInterval(MagicalWinefoxBossEntity boss) {
        int interval = Config.winefoxHitIntervalTicks;
        if (interval <= 0) {
            return false;
        }
        long last = boss.maidspell$lastEffectiveHitTick();
        return last != Long.MIN_VALUE && boss.tickCount - last < interval;
    }

    private static void restore(MagicalWinefoxBossEntity boss, float health, float previous, Cause cause) {
        BossLifecycleAccess.withDataWrite(boss, () -> {
            boss.getEntityData().set(MagicalWinefoxBossEntity.BOSS_HEALTH, health);
            boss.getEntityData().set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), 1.0F);
        });
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
