package com.github.yimeng261.maidspell.item.bauble.spellCore;

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
 * 法术强化核心 - 女仆饰品
 * 根据玩家的铁魔法属性为女仆提供相应的法术强化
 */
public class SpellEnhancementCore extends Item {
    
    public SpellEnhancementCore() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.translatable("item.maidspell.spell_enhancement_core.desc1")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.maidspell.spell_enhancement_core.desc2")
            .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.maidspell.spell_enhancement_core.desc3")
            .withStyle(ChatFormatting.YELLOW));
    }
} 