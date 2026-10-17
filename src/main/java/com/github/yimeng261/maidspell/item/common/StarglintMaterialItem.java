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
 * 带描述的普通材料：星陨石、仪式剑柄、归星。
 *
 * <p>它们都只是带描述的普通材料，没有任何行为；共用一个类、各自给出描述 key，
 * 免得为两行字各开一个文件。归星的行为在星之魔女那边（她认的是手里的物品），
 * 物品本身不需要覆写任何使用入口。
 */
public class StarglintMaterialItem extends Item {
    private final String descriptionKey;

    public StarglintMaterialItem(String descriptionKey) {
        super(new Properties().rarity(Rarity.RARE));
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
    }
}
