package com.github.yimeng261.maidspell.item.taskIcon;

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
 * 近战法术战斗任务图标物品
 * 这个物品仅用作任务图标显示,不应在游戏中获得
 */
public class MeleeTaskIcon extends Item {
    public MeleeTaskIcon() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
        );
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        
        tooltip.add(Component.translatable("item.maidspell.melee_task_icon.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}

