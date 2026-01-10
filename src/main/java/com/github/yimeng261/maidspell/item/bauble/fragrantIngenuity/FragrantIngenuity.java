package com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity;

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
 * 馥郁巧思 Fragrant Ingenuity 饰品物品
 * 女仆进食额外增加好感，女仆喂食主人会赋予随机的1级正面buff
 */
public class FragrantIngenuity extends Item {

    public FragrantIngenuity() {
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.fragrant_ingenuity.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.fragrant_ingenuity.desc2",
            String.format("%d", Config.fragrantIngenuityFavorabilityGain)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.maidspell.fragrant_ingenuity.desc3").withStyle(ChatFormatting.AQUA));
    }
}

