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
 * 星云核心。挑战万法酒狐的信物：拿着它右键秋千上的她即为邀战，同时被消耗。
 *
 * <p>它同时是入场券和奖励——干净打赢会从战利品里还回来一枚，
 * 被判了限制（女仆真伤 / 女仆伤害占比过高）则不还。
 * 于是"让女仆代打"的代价不是少拿个奖励，而是下次重挑战得再攒 4 本旅行日记。
 *
 * <p>交互逻辑不在这里，而在酒狐那一侧：她要判自己是不是坐着、玩家身上有没有不祥之兆，
 * 都是实体自己的状态。这里只是一个带说明的凭证物品。
 */
public class NebulaCoreItem extends Item {

    public NebulaCoreItem() {
        super(new Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.nebula_core.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.nebula_core.desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
