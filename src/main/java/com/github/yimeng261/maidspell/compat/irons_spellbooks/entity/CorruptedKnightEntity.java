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

public class CorruptedKnightEntity extends AbstractSpellMeleeMob implements Enemy {

    public CorruptedKnightEntity(EntityType<? extends CorruptedKnightEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(ForgeMod.ENTITY_REACH.get(), 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected List<AbstractSpell> getAttackSpells() {
        return List.of(
                SpellRegistry.BLOOD_SLASH_SPELL.get(),
                SpellRegistry.BLOOD_NEEDLES_SPELL.get(),
                SpellRegistry.ACUPUNCTURE_SPELL.get(),
                SpellRegistry.FLAMING_STRIKE_SPELL.get());
    }

    @Override
    protected List<AbstractSpell> getMovementSpells() {
        return List.of(SpellRegistry.BLOOD_STEP_SPELL.get());
    }

    @Override
    protected List<AbstractSpell> getSupportSpells() {
        return List.of(SpellRegistry.CHARGE_SPELL.get(), SpellRegistry.OAKSKIN_SPELL.get());
    }

    @Override
    protected AbstractSpell getSingleUseSpell() {
        return SpellRegistry.HEARTSTOP_SPELL.get();
    }

    @Override
    protected int getSingleUseMinLevel() {
        return 2;
    }

    @Override
    protected int getSingleUseMaxLevel() {
        return 4;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        equipAndHideDrop(EquipmentSlot.HEAD, new ItemStack(ItemRegistry.NETHERITE_MAGE_HELMET.get()));
        equipAndHideDrop(EquipmentSlot.CHEST, new ItemStack(ItemRegistry.PYROMANCER_CHESTPLATE.get()));
        equipAndHideDrop(EquipmentSlot.LEGS, new ItemStack(ItemRegistry.CULTIST_LEGGINGS.get()));
        equipAndHideDrop(EquipmentSlot.FEET, new ItemStack(ItemRegistry.CULTIST_BOOTS.get()));
        equipAndHideDrop(EquipmentSlot.MAINHAND, new ItemStack(getClaymoreItem()));
    }
}
