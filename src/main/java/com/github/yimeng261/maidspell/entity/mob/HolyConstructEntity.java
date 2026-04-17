package com.github.yimeng261.maidspell.entity.mob;

import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.GustDefenseGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.PatrolNearLocationGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.SpellBarrageGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardRecoverGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public class HolyConstructEntity extends AbstractSpellCastingMob implements Enemy {
    public HolyConstructEntity(EntityType<? extends AbstractSpellCastingMob> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 40;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GustDefenseGoal(this));
        this.goalSelector.addGoal(2, new SpellBarrageGoal(this, SpellRegistry.ELDRITCH_BLAST_SPELL.get(), 3, 5, 80, 180, 4));
        this.goalSelector.addGoal(3, new HolyConstructAttackGoal(this, 1.25f, 20, 40)
            .setSpells(
                List.of(
                    SpellRegistry.GUIDING_BOLT_SPELL.get(),
                    SpellRegistry.GUIDING_BOLT_SPELL.get(),
                    SpellRegistry.SUNBEAM_SPELL.get(),
                    SpellRegistry.DIVINE_SMITE_SPELL.get(),
                    SpellRegistry.FIREBALL_SPELL.get(),
                    SpellRegistry.ELECTROCUTE_SPELL.get(),
                    SpellRegistry.SHOCKWAVE_SPELL.get(),
                    SpellRegistry.LIGHTNING_LANCE_SPELL.get()
                ),
                List.of(
                    SpellRegistry.COUNTERSPELL_SPELL.get(),
                    SpellRegistry.OAKSKIN_SPELL.get()
                ),
                List.of(SpellRegistry.CHARGE_SPELL.get()),
                List.of()
            )
            .setSpellQuality(0.75f, 1.0f)
            .setAllowFleeing(true)
            .setDrinksPotions());
        this.goalSelector.addGoal(4, new PatrolNearLocationGoal(this, 30, 0.75f));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new WizardRecoverGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
            .add(Attributes.MAX_HEALTH, 200.0)
            .add(Attributes.ARMOR, 20.0)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.ATTACK_KNOCKBACK, 0.0)
            .add(Attributes.FOLLOW_RANGE, 24.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(AttributeRegistry.MAX_MANA.get(), 10000.0)
            .add(AttributeRegistry.COOLDOWN_REDUCTION.get(), 1.9)
            .add(AttributeRegistry.HOLY_SPELL_POWER.get(), 1.2);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_REPAIR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState block) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.8f, 1.0f);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }
}
