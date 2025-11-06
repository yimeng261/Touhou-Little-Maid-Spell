package com.github.yimeng261.maidspell.item.bauble.soulBook;

import com.github.yimeng261.maidspell.Config;
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
 * 魂之书 SoulBook 饰品物品
 * 为女仆提供伤害保护和伤害间隔检测
 */
public class SoulBook extends Item {

    public SoulBook() {
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

        tooltip.add(Component.translatable("item.maidspell.soul_book.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.soul_book.desc2",
            String.format("%.0f", Config.soulBookDamageThresholdPercent * 100)).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.maidspell.soul_book.desc3",
            String.format("%.1f", Config.soulBookDamageIntervalThreshold / 20.0)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.maidspell.soul_book.desc4").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}

