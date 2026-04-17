package com.github.yimeng261.maidspell.item.bauble.springBloomReturn;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class SpringBloomReturn extends Item {
    public SpringBloomReturn() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TooltipHelper.addShiftTooltip(tooltip,
                List.of(Component.translatable("item.maidspell.spring_bloom_return.desc1").withStyle(ChatFormatting.GRAY)),
                List.of(
                        Component.translatable("item.maidspell.spring_bloom_return.desc2",
                                Integer.toString(Config.springBloomReturnMaxStacks),
                                String.format("%.0f", Config.springBloomReturnStackDurationTicks / 20.0)).withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.translatable("item.maidspell.spring_bloom_return.desc3",
                                String.format("%.1f", Config.springBloomReturnDamageThreshold),
                                String.format("%.0f", Config.springBloomReturnDamageThresholdRatio * 100)).withStyle(ChatFormatting.AQUA),
                        Component.translatable("item.maidspell.spring_bloom_return.desc4",
                                String.format("%.0f", Config.springBloomReturnHealRatio * 100),
                                String.format("%.0f", Config.springBloomReturnCooldownRefundRatio * 100)).withStyle(ChatFormatting.GREEN),
                        Component.translatable("item.maidspell.spring_bloom_return.desc5").withStyle(ChatFormatting.YELLOW)
                ));
    }
}
