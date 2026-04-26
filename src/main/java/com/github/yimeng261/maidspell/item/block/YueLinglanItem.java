package com.github.yimeng261.maidspell.item.block;

import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

public class YueLinglanItem extends BlockItem {
    public YueLinglanItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.maidspell.yue_linglan.desc1")
                    .withStyle(ChatFormatting.GRAY)
            ),
            List.of(
                Component.translatable("item.maidspell.yue_linglan.desc2")
                    .withStyle(ChatFormatting.BLUE),
                Component.translatable("item.maidspell.yue_linglan.desc3")
                    .withStyle(ChatFormatting.AQUA)
            ));
    }
}
