package com.github.yimeng261.maidspell.item.bauble.spellRangeTable;

import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 法术射表：让女仆在坐下状态也能继续施放法术。
 */
public class SpellRangeTable extends Item {
    public SpellRangeTable() {
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
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.maidspell.spell_range_table.desc1")
                    .withStyle(ChatFormatting.GRAY)
            ),
            List.of(
                Component.translatable("item.maidspell.spell_range_table.desc2")
                    .withStyle(ChatFormatting.BLUE),
                Component.translatable("item.maidspell.spell_range_table.desc3")
                    .withStyle(ChatFormatting.YELLOW)
            ));
    }
}
