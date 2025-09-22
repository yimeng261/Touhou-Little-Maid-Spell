package com.github.yimeng261.maidspell.item.bauble.hairpin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 发簪 Hairpin 饰品物品
 * 按照现有饰品格式创建的基础物品
 */
public class Hairpin extends Item {

    public Hairpin() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.hairpin.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.hairpin.desc2").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.maidspell.hairpin.desc3").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.maidspell.hairpin.desc4").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.maidspell.hairpin.desc5").withStyle(ChatFormatting.GOLD));
    }
}
