package com.github.yimeng261.maidspell.item.bauble.doubleHeartChain;

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
 * 双心之链
 * 平摊女仆和主人的伤害或转移一方的伤害给另一方
 */
public class DoubleHeartChain extends Item {
    
    public DoubleHeartChain() {
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

        tooltip.add(Component.translatable("item.maidspell.double_heart_chain.desc1")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.double_heart_chain.desc2")
            .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.maidspell.double_heart_chain.desc3",
            String.format("%.0f", Config.doubleHeartChainShareRatio * 100))
            .withStyle(ChatFormatting.YELLOW));
    }
} 