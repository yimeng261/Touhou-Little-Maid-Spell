package com.github.yimeng261.maidspell.item.bauble.rockCrystal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public class RockCrystalBauble implements IExtendBauble {
    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "knockback_resistance");
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if(maid.tickCount % 10 != 0){
            return;
        }
        AttributeInstance knockbackResistance = maid.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance == null) {
            return;
        }
        if (knockbackResistance.getModifier(KNOCKBACK_RESISTANCE_ID) == null) {
            knockbackResistance.addPermanentModifier(
                new AttributeModifier(KNOCKBACK_RESISTANCE_ID, Config.rockCrystalKnockbackResistance, AttributeModifier.Operation.ADD_VALUE)
            );
        }
    }

    @Override
    public void onRemove(EntityMaid maid) {
        // 移除击退抗性加成
        AttributeInstance knockbackResistance = maid.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance == null) {
            return;
        }
        knockbackResistance.removeModifier(KNOCKBACK_RESISTANCE_ID);
    }
}