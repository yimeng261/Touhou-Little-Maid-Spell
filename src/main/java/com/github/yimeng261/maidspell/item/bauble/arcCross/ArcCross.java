package com.github.yimeng261.maidspell.item.bauble.arcCross;

import com.github.yimeng261.maidspell.utils.TooltipHelper;
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
 * 弧光十字 - 铁魔法联动饰品
 */
public class ArcCross extends Item {
    public ArcCross() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
                                @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        TooltipHelper.addShiftTooltip(tooltip,
                List.of(
                        Component.translatable("item.maidspell.arc_cross.desc1")
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("item.maidspell.arc_cross.desc2")
                                .withStyle(ChatFormatting.AQUA)
                ),
                List.of(
                        Component.translatable("item.maidspell.arc_cross.desc3")
                                .withStyle(ChatFormatting.GOLD),
                        Component.translatable("item.maidspell.arc_cross.desc4")
                                .withStyle(ChatFormatting.LIGHT_PURPLE)
                ));
    }
}
