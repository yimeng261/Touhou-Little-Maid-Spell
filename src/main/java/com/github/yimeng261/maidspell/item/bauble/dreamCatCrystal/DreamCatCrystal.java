package com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal;

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
 * 梦云水晶 - 顶级女仆综合型饰品
 * 集成大量被动效果、战斗增强、维度加成和特殊机制
 */
public class DreamCatCrystal extends Item {

    public DreamCatCrystal() {
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
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc1")
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc2")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc3")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc4")
            .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
