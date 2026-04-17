package com.github.yimeng261.maidspell.item.bauble.floatingFoxLeaf;

import com.github.yimeng261.maidspell.utils.TooltipHelper;
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
 * 浮波狐叶
 * 让女仆借狐影荷叶之灵踏水而行。
 */
public class FloatingFoxLeaf extends Item {
    public FloatingFoxLeaf() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.maidspell.floating_fox_leaf.desc1")
                    .withStyle(ChatFormatting.GRAY)
            ),
            List.of(
                Component.translatable("item.maidspell.floating_fox_leaf.desc2")
                    .withStyle(ChatFormatting.AQUA),
                Component.translatable("item.maidspell.floating_fox_leaf.desc3")
                    .withStyle(ChatFormatting.GREEN)
            ));
    }
}
