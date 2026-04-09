package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.base.AbstractSpellMeleeMob;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import java.util.List;

public class ShadowAssassinEntity extends AbstractSpellMeleeMob implements Enemy {
    public ShadowAssassinEntity(EntityType<? extends ShadowAssassinEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(ForgeMod.ENTITY_REACH.get(), 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected int getXpRewardValue() {
        return 20;
    }

    @Override
    protected float getComboChance() {
        return 0.55f;
    }

    @Override
    protected int getSpellAttackIntervalMin() {
        return 30;
    }

    @Override
    protected int getSpellAttackIntervalMax() {
        return 55;
    }

    @Override
    protected List<AbstractSpell> getAttackSpells() {
        return List.of(
                SpellRegistry.THROW_SPELL.get(),
                SpellRegistry.MAGIC_MISSILE_SPELL.get(),
                SpellRegistry.MAGIC_ARROW_SPELL.get(),
                SpellRegistry.SHADOW_SLASH.get());
    }

    @Override
    protected List<AbstractSpell> getMovementSpells() {
        return List.of(SpellRegistry.BLOOD_STEP_SPELL.get());
    }

    @Override
    protected List<AbstractSpell> getSupportSpells() {
        return List.of(SpellRegistry.INVISIBILITY_SPELL.get(), SpellRegistry.ECHOING_STRIKES_SPELL.get());
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        equipAndHideDrop(EquipmentSlot.LEGS, new ItemStack(ItemRegistry.SHADOWWALKER_LEGGINGS.get()));
        equipAndHideDrop(EquipmentSlot.FEET, new ItemStack(ItemRegistry.SHADOWWALKER_BOOTS.get()));
        equipAndHideDrop(EquipmentSlot.MAINHAND, new ItemStack(ItemRegistry.AMETHYST_RAPIER.get()));
    }
}
