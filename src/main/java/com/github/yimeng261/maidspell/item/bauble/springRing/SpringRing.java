package com.github.yimeng261.maidspell.item.bauble.springRing;

import com.github.yimeng261.maidspell.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

public class SpringRing extends Item {
    public SpringRing() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nonnull TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.spring_ring.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.spring_ring.desc2")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.maidspell.spring_ring.desc3",
                String.format("%.0f", Config.springRingMaxDamageBonus * 100))
                .withStyle(ChatFormatting.YELLOW));
    }
}
