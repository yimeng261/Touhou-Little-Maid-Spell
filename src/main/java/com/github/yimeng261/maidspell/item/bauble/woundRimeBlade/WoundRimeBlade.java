package com.github.yimeng261.maidspell.item.bauble.woundRimeBlade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 破愈咒锋饰品物品
 * 禁止敌方治疗
 */
public class WoundRimeBlade extends Item {
    public WoundRimeBlade() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.wound_rime_blade.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.wound_rime_blade.desc2")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.maidspell.wound_rime_blade.desc3")
                .withStyle(ChatFormatting.RED));
    }
}
