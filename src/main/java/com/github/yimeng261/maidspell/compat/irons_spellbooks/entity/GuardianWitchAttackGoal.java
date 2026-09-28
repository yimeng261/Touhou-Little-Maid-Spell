package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;

/**
 * 在 ISS 加权选法术后维持一轮连发。
 * ISS 先重置攻击计时器再选法术，因此首发也须覆写已设定的长间隔。
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
