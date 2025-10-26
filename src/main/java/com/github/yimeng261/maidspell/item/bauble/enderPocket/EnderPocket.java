package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 末影腰包饰品物品
 * 允许玩家通过按键远程访问佩戴此饰品的女仆的背包
 */
public class EnderPocket extends Item {
    public EnderPocket() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
        );
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.ender_pocket.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.ender_pocket.desc2")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.maidspell.ender_pocket.desc3")
                .withStyle(ChatFormatting.GOLD));
    }
}
