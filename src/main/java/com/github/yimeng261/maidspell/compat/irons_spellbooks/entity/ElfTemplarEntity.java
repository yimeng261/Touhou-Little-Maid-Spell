package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.base.AbstractSpellMeleeMob;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import java.util.List;

public class ElfTemplarEntity extends AbstractSpellMeleeMob {

    public ElfTemplarEntity(EntityType<? extends ElfTemplarEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(ForgeMod.ENTITY_REACH.get(), 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(AttributeRegistry.CAST_TIME_REDUCTION.get(), 1.5);
    }

    @Override
    protected boolean addDefaultPlayerTargetGoal() {
        return false;
    }

    @Override
    protected List<AbstractSpell> getAttackSpells() {
        return List.of(
                SpellRegistry.POISON_ARROW_SPELL.get(),
                SpellRegistry.FIREFLY_SWARM_SPELL.get(),
                SpellRegistry.ROOT_SPELL.get(),
                SpellRegistry.GUST_SPELL.get(),
                SpellRegistry.ARROW_VOLLEY_SPELL.get());
    }

    @Override
    protected float getComboChance() {
        return 0.2f;
    }

    @Override
    protected float getMeleeBiasMin() {
        return 0.05f;
    }

    @Override
    protected float getMeleeBiasMax() {
        return 0.15f;
    }

    @Override
    protected int getSpellAttackIntervalMin() {
        return 20;
    }

    @Override
    protected int getSpellAttackIntervalMax() {
        return 40;
    }

    @Override
    protected List<AbstractSpell> getMovementSpells() {
        return List.of(SpellRegistry.FROST_STEP_SPELL.get());
    }

    @Override
    protected List<AbstractSpell> getSupportSpells() {
        return List.of(SpellRegistry.HEAL_SPELL.get(), SpellRegistry.OAKSKIN_SPELL.get());
    }

    @Override
    protected void registerAdditionalGoals() {
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        equipAndHideDrop(EquipmentSlot.MAINHAND, new ItemStack(getClaymoreItem()));
    }
}
