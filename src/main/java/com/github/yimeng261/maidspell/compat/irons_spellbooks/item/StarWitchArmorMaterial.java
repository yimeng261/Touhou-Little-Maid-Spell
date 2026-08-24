package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.armor.IronsExtendedArmorMaterial;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Map;
import java.util.UUID;

/**
 * 星之魔女法帽的护甲材质：护甲 3、韧性 3、耐久 600，紫水晶碎片修复，
 * 另外提供 +200 最大法力值、+15% 法术强度、+10% 法术冷却缩减。
 */
public enum StarWitchArmorMaterial implements IronsExtendedArmorMaterial {
    STAR_WITCH;

    private static final UUID MANA_MODIFIER = UUID.fromString("6f1c9a2e-4f2b-4d51-9a3f-2b7d1f6e5c01");
    private static final UUID SPELL_POWER_MODIFIER = UUID.fromString("6f1c9a2e-4f2b-4d51-9a3f-2b7d1f6e5c02");
    private static final UUID COOLDOWN_MODIFIER = UUID.fromString("6f1c9a2e-4f2b-4d51-9a3f-2b7d1f6e5c03");

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 600;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return type == ArmorItem.Type.HELMET ? 3 : 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 22;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.AMETHYST_SHARD);
    }

    @Override
    public String getName() {
        return "star_witch";
    }

    @Override
    public float getToughness() {
        return 3.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }

    @Override
    public Map<Attribute, AttributeModifier> getAdditionalAttributes() {
        return Map.of(
                AttributeRegistry.MAX_MANA.get(),
                new AttributeModifier(MANA_MODIFIER, "Star witch max mana", 200.0D,
                        AttributeModifier.Operation.ADDITION),
                AttributeRegistry.SPELL_POWER.get(),
                new AttributeModifier(SPELL_POWER_MODIFIER, "Star witch spell power", 0.15D,
                        AttributeModifier.Operation.MULTIPLY_BASE),
                AttributeRegistry.COOLDOWN_REDUCTION.get(),
                new AttributeModifier(COOLDOWN_MODIFIER, "Star witch cooldown reduction", 0.10D,
                        AttributeModifier.Operation.MULTIPLY_BASE));
    }
}
