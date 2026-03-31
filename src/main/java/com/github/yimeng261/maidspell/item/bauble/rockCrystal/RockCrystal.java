package com.github.yimeng261.maidspell.item.bauble.rockCrystal;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 磐石魔晶
 * 大幅提高女仆的抗击退能力
 */
public class RockCrystal extends Item {
    
    public RockCrystal() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.maidspell.rock_crystal.desc1")
                    .withStyle(ChatFormatting.GRAY)
            ),
            List.of(
                Component.translatable("item.maidspell.rock_crystal.desc2",
                    String.format("%d", Config.rockCrystalKnockbackResistance))
                    .withStyle(ChatFormatting.BLUE)
            ));
    }
} 
