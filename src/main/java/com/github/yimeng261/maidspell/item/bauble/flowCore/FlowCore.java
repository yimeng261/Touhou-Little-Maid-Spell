package com.github.yimeng261.maidspell.item.bauble.flowCore;

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

public class FlowCore extends Item {
    public FlowCore() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.flow_core.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.flow_core.desc2",
                String.format("%.1f", Config.flowCoreTickInterval / 20.0),
                String.format("%.1f", Config.flowCoreHealthRegenRate * 100))
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.maidspell.flow_core.desc3",
                String.format("%.0f", Config.flowCoreDamageReduction * 100))
                .withStyle(ChatFormatting.YELLOW));
    }
}
