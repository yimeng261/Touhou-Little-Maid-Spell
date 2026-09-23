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
 * 打造星芒短剑的两件材料：星陨石与仪式剑柄。
 *
 * <p>两者都只是带描述的普通材料，没有任何行为；共用一个类、各自给出描述 key，
 * 免得为两行字各开一个文件。
 */
public class StarglintMaterialItem extends Item {
    private final String descriptionKey;

    public StarglintMaterialItem(String descriptionKey) {
        super(new Properties().rarity(Rarity.RARE));
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
    }
}
