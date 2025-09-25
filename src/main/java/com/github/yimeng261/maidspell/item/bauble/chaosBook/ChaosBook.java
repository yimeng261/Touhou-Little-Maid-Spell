package com.github.yimeng261.maidspell.item.bauble.chaosBook;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 混沌之书 - 女仆饰品
 * 将女仆的伤害转为InfoDamageSources类型
 */
public class ChaosBook extends Item {
    
    public ChaosBook() {
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
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.chaos_book.desc1")
            .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.maidspell.chaos_book.desc2")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.maidspell.chaos_book.desc3")
            .withStyle(ChatFormatting.GRAY));
    }
}
