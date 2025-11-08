package com.github.yimeng261.maidspell.item.bauble.spellOverlimitCore;

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
 * 法术超限核心饰品物品
 * 突破法术限制，提供超限能力
 */
public class SpellOverlimitCore extends Item {
    public SpellOverlimitCore() {
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
        
        tooltip.add(Component.translatable("item.maidspell.spell_overlimit_core.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.spell_overlimit_core.desc2")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.maidspell.spell_overlimit_core.desc3")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.maidspell.spell_overlimit_core.desc4")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}

