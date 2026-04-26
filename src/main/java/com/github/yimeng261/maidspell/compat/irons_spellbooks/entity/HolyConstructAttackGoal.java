package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class HolyConstructAttackGoal extends WizardAttackGoal {
    private static final float PREFERRED_COMBAT_RANGE = 6.0f;
    private static final float PREFERRED_COMBAT_RANGE_SQR = PREFERRED_COMBAT_RANGE * PREFERRED_COMBAT_RANGE;
    private static final float TOO_CLOSE_RANGE = 3.0f;
    private static final float TOO_CLOSE_RANGE_SQR = TOO_CLOSE_RANGE * TOO_CLOSE_RANGE;
    private static final int CHARGE_REUSE_COOLDOWN_TICKS = 20 * 60;
    private static final int CHARGE_PRIORITY_BONUS = 90;
    private static final int OAKSKIN_REUSE_COOLDOWN_TICKS = 20 * 25;
    private static final int OAKSKIN_PRIORITY_BONUS = 65;
    private static final AbstractSpell NO_SPELL = SpellRegistry.none();

    private long lastChargeCastTick = Long.MIN_VALUE;
    private long lastOakskinCastTick = Long.MIN_VALUE;

    public HolyConstructAttackGoal(IMagicEntity spellCastingMob, double speedModifier, int attackIntervalMin, int attackIntervalMax) {
        super(spellCastingMob, speedModifier, attackIntervalMin, attackIntervalMax);
    }

    @Override
    protected int getMovementWeight() {
        if (mob.tickCount - lastChargeCastTick < CHARGE_REUSE_COOLDOWN_TICKS) {
            return 0;
        }
        return super.getMovementWeight() + CHARGE_PRIORITY_BONUS;
    }

    @Override
    protected int getDefenseWeight() {
        int weight = super.getDefenseWeight();
        if (isOakskinAvailable()) {
            weight += OAKSKIN_PRIORITY_BONUS;
        }
        return weight;
    }

    @Override
    protected void doSpellAction() {
        if (trySingleUseSpell()) {
            return;
        }
        castSelectedSpell();
    }

    private boolean trySingleUseSpell() {
        if (spellCastingMob.getHasUsedSingleAttack() || singleUseSpell == NO_SPELL || singleUseDelay > 0) {
            return false;
        }
        spellCastingMob.setHasUsedSingleAttack(true);
        spellCastingMob.initiateCastSpell(singleUseSpell, singleUseLevel);
        markSpellCast(singleUseSpell);
        fleeCooldown = 7 + singleUseSpell.getCastTime(singleUseLevel);
        return true;
    }

    private void castSelectedSpell() {
        AbstractSpell spell = getNextSpellType();
        int spellLevel = randomSpellLevel(spell);
        if (spell.shouldAIStopCasting(spellLevel, mob, target)) {
            spellAttackDelay = 5;
            return;
        }
        spellCastingMob.initiateCastSpell(spell, spellLevel);
        markSpellCast(spell);
        fleeCooldown = 7 + spell.getCastTime(spellLevel);
    }

    private int randomSpellLevel(AbstractSpell spell) {
        float quality = Mth.lerp(mob.getRandom().nextFloat(), minSpellQuality, maxSpellQuality);
        return Math.max((int) (spell.getMaxLevel() * quality), 1);
    }

    @Override
    protected AbstractSpell getNextSpellType() {
        List<SpellBucket> buckets = new ArrayList<>(4);
        int total = appendWeightedCategories(buckets);
        if (total <= 0) {
            return NO_SPELL;
        }

        ArrayList<AbstractSpell> spellList = pickBucket(buckets, mob.getRandom().nextInt(total));
        lastSpellCategory = spellList;
        if (shouldDrinkInsteadOfCast(spellList)) {
            spellCastingMob.startDrinkingPotion();
            return NO_SPELL;
        }
        return chooseSpellFromCategory(spellList);
    }

    private int appendWeightedCategories(List<SpellBucket> buckets) {
        int total = 0;
        total = appendCategory(buckets, total, attackSpells, getAttackWeight());
        total = appendCategory(buckets, total, defenseSpells, getDefenseWeight() - repeatPenalty(defenseSpells, 100));
        total = appendCategory(buckets, total, movementSpells, getMovementWeight() - repeatPenalty(movementSpells, 50));
        return appendCategory(buckets, total, supportSpells, getSupportWeight() - repeatPenalty(supportSpells, 100), drinksPotions);
    }

    private int repeatPenalty(ArrayList<AbstractSpell> category, int penalty) {
        return lastSpellCategory == category ? penalty : 0;
    }

    private int appendCategory(List<SpellBucket> buckets, int currentTotal, ArrayList<AbstractSpell> spells, int weight) {
        return appendCategory(buckets, currentTotal, spells, weight, false);
    }

    private int appendCategory(List<SpellBucket> buckets, int currentTotal, ArrayList<AbstractSpell> spells, int weight, boolean allowEmptyCategory) {
        if (weight <= 0 || (!allowEmptyCategory && spells.isEmpty())) {
            return currentTotal;
        }
        int nextTotal = currentTotal + weight;
        buckets.add(new SpellBucket(nextTotal, spells));
        return nextTotal;
    }

    private ArrayList<AbstractSpell> pickBucket(List<SpellBucket> buckets, int roll) {
        for (SpellBucket bucket : buckets) {
            if (roll < bucket.ceiling()) {
                return bucket.spells();
            }
        }
        return buckets.get(buckets.size() - 1).spells();
    }

    private boolean shouldDrinkInsteadOfCast(ArrayList<AbstractSpell> spellList) {
        return drinksPotions && spellList == supportSpells && (supportSpells.isEmpty() || mob.getRandom().nextFloat() < 0.5f);
    }

    private AbstractSpell chooseSpellFromCategory(ArrayList<AbstractSpell> spellList) {
        if (spellList != defenseSpells) {
            return randomSpell(spellList);
        }

        AbstractSpell oakskin = SpellRegistry.OAKSKIN_SPELL.get();
        if (isOakskinAvailable() && defenseSpells.contains(oakskin) && mob.getRandom().nextFloat() < 0.8f) {
            return oakskin;
        }

        ArrayList<AbstractSpell> candidates = new ArrayList<>(spellList);
        if (!isOakskinAvailable()) {
            candidates.remove(oakskin);
        }
        return candidates.isEmpty() ? randomSpell(spellList) : randomSpell(candidates);
    }

    private AbstractSpell randomSpell(ArrayList<AbstractSpell> spellList) {
        return spellList.get(mob.getRandom().nextInt(spellList.size()));
    }

    private boolean isOakskinAvailable() {
        return mob.tickCount - lastOakskinCastTick >= OAKSKIN_REUSE_COOLDOWN_TICKS;
    }

    private void markSpellCast(AbstractSpell spell) {
        if (spell == SpellRegistry.CHARGE_SPELL.get()) {
            lastChargeCastTick = mob.tickCount;
        }
        if (spell == SpellRegistry.OAKSKIN_SPELL.get()) {
            lastOakskinCastTick = mob.tickCount;
        }
    }

    @Override
    protected void doMovement(double distanceSquared) {
        double speed = currentMovementSpeed();
        mob.lookAt(target, 30, 30);

        if (shouldBackAway(distanceSquared)) {
            backAway(speed);
            return;
        }
        if (shouldCircleTarget(distanceSquared)) {
            circleTarget(distanceSquared, speed);
            return;
        }
        approachTargetOnInterval();
    }

    private double currentMovementSpeed() {
        return (spellCastingMob.isCasting() ? 0.75f : 1f) * movementSpeed();
    }

    private boolean shouldBackAway(double distanceSquared) {
        return allowFleeing && distanceSquared < TOO_CLOSE_RANGE_SQR;
    }

    private void backAway(double speed) {
        Vec3 flee = DefaultRandomPos.getPosAway(this.mob, 16, 7, target.position());
        if (flee != null) {
            this.mob.getNavigation().moveTo(flee.x, flee.y, flee.z, speed * 1.25);
        } else {
            mob.getMoveControl().strafe(-(float) speed * getStrafeMultiplier(), (float) speed * getStrafeMultiplier());
        }
    }

    private boolean shouldCircleTarget(double distanceSquared) {
        return hasLineOfSight && distanceSquared <= PREFERRED_COMBAT_RANGE_SQR * 1.35f;
    }

    private void circleTarget(double distanceSquared, double speed) {
        this.mob.getNavigation().stop();
        maybeFlipStrafeDirection();

        float strafeForward = distanceSquared < PREFERRED_COMBAT_RANGE_SQR * 0.7f ? -0.12f : 0.08f;
        int strafeDir = strafingClockwise ? 1 : -1;
        mob.getMoveControl().strafe(strafeForward * getStrafeMultiplier(), (float) speed * 0.85f * strafeDir * getStrafeMultiplier());
        if (mob.horizontalCollision && mob.getRandom().nextFloat() < 0.1f) {
            tryJump();
        }
    }

    private void maybeFlipStrafeDirection() {
        if (++strafeTime > 20 && mob.getRandom().nextDouble() < 0.12) {
            strafingClockwise = !strafingClockwise;
            strafeTime = 0;
        }
    }

    private void approachTargetOnInterval() {
        if (mob.tickCount % 5 == 0) {
            this.mob.getNavigation().moveTo(this.target, speedModifier * 1.15);
        }
    }

    @Override
    protected double movementSpeed() {
        return speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2;
    }

    private record SpellBucket(int ceiling, ArrayList<AbstractSpell> spells) {
    }
}
