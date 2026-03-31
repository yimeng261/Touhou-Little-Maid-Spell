package com.github.yimeng261.maidspell.item.bauble.silverCercis;

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
 * 银荆
 * 神秘的银色荆棘饰品
 */
public class SilverCercis extends Item {
    
    public SilverCercis() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)
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
                Component.translatable("item.maidspell.sliver_cercis.desc1").withStyle(ChatFormatting.GRAY),
                Component.translatable("item.maidspell.sliver_cercis.desc3").withStyle(ChatFormatting.RED)
            ),
            List.of(
                Component.translatable("item.maidspell.sliver_cercis.desc2",
                    String.format("%d", Config.silverCercisTriggerCount),
                    String.format("%d", Config.silverCercisCooldownTicks),
                    String.format("%.0f", Config.silverCercisTrueDamageMultiplier * 100)).withStyle(ChatFormatting.BLUE)
            ));
    }
} 
