package com.github.yimeng261.maidspell.item.bauble.rockCrystal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class RockCrystalBauble implements IExtendBauble {
    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("8b5c7a26-3e4f-4c45-a7b3-2f8d9e1a5c47");
    
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        // 每tick检查并确保击退抗性存在
        if(maid.tickCount % 10 != 0){
            return;
        }
        if (maid.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifier(KNOCKBACK_RESISTANCE_UUID) == null) {
            maid.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(
                new AttributeModifier(KNOCKBACK_RESISTANCE_UUID, "Rock Crystal Knockback Resistance", 
                    8, AttributeModifier.Operation.ADDITION)
            );
        }
    }
    
    @Override
    public void onRemove(EntityMaid maid) {
        // 移除击退抗性加成
        maid.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(KNOCKBACK_RESISTANCE_UUID);
    }
} 