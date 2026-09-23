package com.github.yimeng261.maidspell.item.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 星芒短剑：递给星之魔女后开启切磋。
 */
public class StarglintDaggerItem extends Item {
    public StarglintDaggerItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.starglint_dagger.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.starglint_dagger.desc2")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
