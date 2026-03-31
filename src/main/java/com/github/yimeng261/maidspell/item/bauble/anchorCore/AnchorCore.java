package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.util.List;

/**
 * 锚定核心饰品物品
 * 为女仆提供全面的保护，防止被其他模组的机制影响
 */
public class AnchorCore extends Item {
    public AnchorCore() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.maidspell.anchor_core.desc1")
                    .withStyle(ChatFormatting.GRAY),
                Component.translatable("item.maidspell.anchor_core.desc5")
                    .withStyle(ChatFormatting.RED)
            ),
            List.of(
                Component.translatable("item.maidspell.anchor_core.desc2")
                    .withStyle(ChatFormatting.GOLD),
                Component.translatable("item.maidspell.anchor_core.desc3")
                    .withStyle(ChatFormatting.YELLOW),
                Component.translatable("item.maidspell.anchor_core.desc4")
                    .withStyle(ChatFormatting.YELLOW)
            ));
    }
}
