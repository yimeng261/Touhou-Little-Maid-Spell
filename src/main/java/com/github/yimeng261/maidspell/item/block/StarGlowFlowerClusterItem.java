package com.github.yimeng261.maidspell.item.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StarGlowFlowerClusterItem extends BlockItem {
    public StarGlowFlowerClusterItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.star_glow_flower_cluster.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
