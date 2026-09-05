package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;

/**
 * 在 ISS 那套加权选法术之上，给指定的一个法术加「连发」。
 *
 * <p>{@link WizardAttackGoal} 每次开火都重新抽一个法术，两发之间隔
 * {@code spellAttackIntervalMin..Max}——魔法飞弹这种「一梭子」的法术照这个节奏放就是单发点射。
 * 这里的做法是：抽中连发法术时记下还欠几发，之后的 {@link #getNextSpellType()} 直接把它还回去，
 * 并把开火间隔压到 {@link #BURST_INTERVAL}，一梭子放完再交回原来的加权逻辑。
 *
 * <p>有个顺序上的坑：{@code handleAttackLogic} 是<b>先</b> {@code resetSpellAttackTimer} <b>再</b>
 * {@code doSpellAction} 的，也就是说等 {@code getNextSpellType} 抽中连发时，长间隔已经写进
 * {@code spellAttackDelay} 了。所以起手那一发要在 {@code getNextSpellType} 里把间隔再改回来，
 * 光靠重写 {@code resetSpellAttackTimer} 只能管到第二发往后。
 */
public class GuardianWitchAttackGoal extends WizardAttackGoal {
    /** 连发期间的开火间隔（tick）。魔法飞弹是瞬发，6 tick 一发看着才像连射。 */
    private static final int BURST_INTERVAL = 6;

    private final AbstractSpell burstSpell;
    private final int burstMin;
    private final int burstMax;

    /** 这一梭子还欠几发。起手那一发不算在内。 */
    private int burstRemaining;

    public GuardianWitchAttackGoal(IMagicEntity mob, double speedModifier, int attackIntervalMin, int attackIntervalMax,
                                   AbstractSpell burstSpell, int burstMin, int burstMax) {
        super(mob, speedModifier, attackIntervalMin, attackIntervalMax);
        this.burstSpell = burstSpell;
        this.burstMin = burstMin;
        this.burstMax = burstMax;
    }

    @Override
    protected AbstractSpell getNextSpellType() {
        if (this.burstRemaining > 0) {
            this.burstRemaining--;
            return this.burstSpell;
        }
        AbstractSpell spell = super.getNextSpellType();
        if (spell == this.burstSpell) {
            // 连同起手这一发凑够 burstMin..burstMax 发。
            this.burstRemaining = this.burstMin - 1 + this.mob.getRandom().nextInt(this.burstMax - this.burstMin + 1);
            this.spellAttackDelay = BURST_INTERVAL;
        }
        return spell;
    }

    @Override
    protected void resetSpellAttackTimer(double distanceSquared) {
        if (this.burstRemaining > 0) {
            this.spellAttackDelay = BURST_INTERVAL;
            return;
        }
        super.resetSpellAttackTimer(distanceSquared);
    }

    @Override
    public void stop() {
        // 打断的那一梭子不留到下一场架里接着放。
        this.burstRemaining = 0;
        super.stop();
    }
}
