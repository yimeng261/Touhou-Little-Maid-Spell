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

/** 镇石物品的提示说明；只阻止敌对生物自然生成。 */
public class SuppressionStoneItem extends BlockItem {
    public SuppressionStoneItem(Block block) {
        super(block, new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.touhou_little_maid_spell.suppression_stone.desc1")
                    .withStyle(ChatFormatting.GRAY)
            ),
            List.of(
                Component.translatable("item.touhou_little_maid_spell.suppression_stone.desc2")
                    .withStyle(ChatFormatting.GOLD),
                Component.translatable("item.touhou_little_maid_spell.suppression_stone.desc3")
                    .withStyle(ChatFormatting.DARK_PURPLE)
            ));
    }
}
