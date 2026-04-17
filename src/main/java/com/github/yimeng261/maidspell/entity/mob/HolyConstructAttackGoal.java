package com.github.yimeng261.maidspell.entity.mob;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.NavigableMap;
import java.util.TreeMap;

public class HolyConstructAttackGoal extends WizardAttackGoal {
    private static final int CHARGE_REUSE_COOLDOWN_TICKS = 20 * 60;
    private static final int CHARGE_PRIORITY_BONUS = 90;
    private static final int OAKSKIN_REUSE_COOLDOWN_TICKS = 20 * 25;
    private static final int OAKSKIN_PRIORITY_BONUS = 65;
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
        if (!spellCastingMob.getHasUsedSingleAttack() && singleUseSpell != SpellRegistry.none() && singleUseDelay <= 0) {
            spellCastingMob.setHasUsedSingleAttack(true);
            spellCastingMob.initiateCastSpell(singleUseSpell, singleUseLevel);
            if (singleUseSpell == SpellRegistry.CHARGE_SPELL.get()) {
                lastChargeCastTick = mob.tickCount;
            }
            fleeCooldown = 7 + singleUseSpell.getCastTime(singleUseLevel);
            return;
        }

        AbstractSpell spell = getNextSpellType();
        int spellLevel = (int) (spell.getMaxLevel() * Mth.lerp(mob.getRandom().nextFloat(), minSpellQuality, maxSpellQuality));
        spellLevel = Math.max(spellLevel, 1);

        if (!spell.shouldAIStopCasting(spellLevel, mob, target)) {
            spellCastingMob.initiateCastSpell(spell, spellLevel);
            markSpellCast(spell);
            fleeCooldown = 7 + spell.getCastTime(spellLevel);
        } else {
            spellAttackDelay = 5;
        }
    }

    @Override
    protected AbstractSpell getNextSpellType() {
        NavigableMap<Integer, ArrayList<AbstractSpell>> weightedSpells = new TreeMap<>();
        int attackWeight = getAttackWeight();
        int defenseWeight = getDefenseWeight() - (lastSpellCategory == defenseSpells ? 100 : 0);
        int movementWeight = getMovementWeight() - (lastSpellCategory == movementSpells ? 50 : 0);
        int supportWeight = getSupportWeight() - (lastSpellCategory == supportSpells ? 100 : 0);
        int total = 0;

        if (!attackSpells.isEmpty() && attackWeight > 0) {
            total += attackWeight;
            weightedSpells.put(total, attackSpells);
        }
        if (!defenseSpells.isEmpty() && defenseWeight > 0) {
            total += defenseWeight;
            weightedSpells.put(total, defenseSpells);
        }
        if (!movementSpells.isEmpty() && movementWeight > 0) {
            total += movementWeight;
            weightedSpells.put(total, movementSpells);
        }
        if ((!supportSpells.isEmpty() || drinksPotions) && supportWeight > 0) {
            total += supportWeight;
            weightedSpells.put(total, supportSpells);
        }

        if (total <= 0) {
            return SpellRegistry.none();
        }

        int seed = mob.getRandom().nextInt(total);
        ArrayList<AbstractSpell> spellList = weightedSpells.higherEntry(seed).getValue();
        lastSpellCategory = spellList;
        if (drinksPotions && spellList == supportSpells) {
            if (supportSpells.isEmpty() || mob.getRandom().nextFloat() < .5f) {
                spellCastingMob.startDrinkingPotion();
                return SpellRegistry.none();
            }
        }
        return chooseSpellFromCategory(spellList);
    }

    private AbstractSpell chooseSpellFromCategory(ArrayList<AbstractSpell> spellList) {
        if (spellList == defenseSpells) {
            AbstractSpell oakskin = SpellRegistry.OAKSKIN_SPELL.get();
            if (isOakskinAvailable() && defenseSpells.contains(oakskin) && mob.getRandom().nextFloat() < 0.8f) {
                return oakskin;
            }

            ArrayList<AbstractSpell> fallback = new ArrayList<>(spellList);
            if (!isOakskinAvailable()) {
                fallback.remove(oakskin);
            }
            if (!fallback.isEmpty()) {
                return fallback.get(mob.getRandom().nextInt(fallback.size()));
            }
        }
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
}
