package com.github.yimeng261.maidspell.item.block;

import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class JingxuYoulanItem extends BlockItem {
    public JingxuYoulanItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.maidspell.jingxu_youlan.desc1")
                    .withStyle(ChatFormatting.GRAY)
            ),
            List.of(
                Component.translatable("item.maidspell.jingxu_youlan.desc2")
                    .withStyle(ChatFormatting.BLUE),
                Component.translatable("item.maidspell.jingxu_youlan.desc3")
                    .withStyle(ChatFormatting.DARK_PURPLE)
            ));
    }
}
