package com.github.yimeng261.maidspell.item.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 星云核心：合格挑战的奖励，用于梦云水晶合成与装备交易。
 */
public class NebulaCoreItem extends Item {

    public NebulaCoreItem() {
        super(new Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.nebula_core.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.nebula_core.desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
