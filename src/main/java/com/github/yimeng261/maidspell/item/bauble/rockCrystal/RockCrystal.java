package com.github.yimeng261.maidspell.item.bauble.rockCrystal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 磐石魔晶
 * 大幅提高女仆的抗击退能力
 */
public class RockCrystal extends Item {

    public RockCrystal() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nonnull TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.rock_crystal.desc1")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.rock_crystal.desc2")
            .withStyle(ChatFormatting.BLUE));
    }
}