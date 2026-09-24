package com.github.yimeng261.maidspell.item.bauble.staranchorPearl;

import com.github.yimeng261.maidspell.effect.MaidSpellEffects;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 星锚珍珠。
 *
 * <p>两种用法共用一件物品：
 * <ul>
 *   <li><b>玩家</b>：右键消耗 {@link #PLAYER_USE_COST} 点耐久，拿到 30 秒的「虚空漫步」，
 *       期间免疫虚空伤害；</li>
 *   <li><b>女仆</b>：当作饰品佩戴，替她挡下虚空伤害，每挡一发扣 1 点耐久，
 *       见 {@link StaranchorPearlBauble}。</li>
 * </ul>
 *
 * <p><b>两边的单价不一样是有意的</b>：玩家那一下是主动买来的 30 秒免疫，按次收贵一点；
 * 女仆那边是跟着伤害结算走的被动抵挡，照原版保护类饰品的 1 点／次。
 *
 * <p><b>耐久为什么这么给</b>：参照车万女仆原版的保护类饰品 —— 那几个都是
 * {@code new Item.Properties().durability(n).setNoRepair()}，靠 {@code hurtAndBreak} 消耗，
 * 用完即碎、不可修复（{@code ItemDamageableBauble}）。这里取同一档的 64
 * （溺水/弹射物保护是 64，爆炸/摔落是 32，火焰/魔法是 128），
 * 于是珍珠既是「一次性的应急道具」又是「会磨损的饰品」：玩家侧撑 8 次，女仆侧撑 64 次。
 */
public class StaranchorPearl extends Item {
    /** 虚空漫步时长：30 秒。 */
    public static final int VOID_WALK_TICKS = 30 * 20;

    /** 和原版保护类饰品同一档的耐久。 */
    public static final int DURABILITY = 64;

    /** 玩家右键一次的耐久开销。8 点配 64 点总耐久，正好 8 次。 */
    public static final int PLAYER_USE_COST = 8;

    /**
     * 女仆那边每挡下一发虚空伤害的耐久开销。
     *
     * <p>和玩家侧不同价：玩家是主动买 30 秒免疫，贵一点；女仆是被动抵挡，
     * 照原版保护类饰品的 1 点／次。
     */
    public static final int MAID_BLOCK_COST = 1;

    public StaranchorPearl() {
        super(new Properties()
                .stacksTo(1)
                .durability(DURABILITY)
                .setNoRepair()
                .rarity(Rarity.RARE));
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        // 和原版保护类饰品一致：附魔光效。
        return true;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 状态还在就不重复吃耐久。虚空漫步本身有 30 秒，续一次只是白白磨掉一点珍珠。
        if (player.hasEffect(MaidSpellEffects.VOID_WALK.get())) {
            return InteractionResultHolder.pass(stack);
        }

        if (level instanceof ServerLevel serverLevel) {
            player.addEffect(new MobEffectInstance(MaidSpellEffects.VOID_WALK.get(), VOID_WALK_TICKS, 0,
                    false, false, true));
            VoidWalkEffect.spawnRing(serverLevel, player);
            // 消耗耐久。耐久见底时 hurtAndBreak 会把这颗珍珠直接销毁。
            stack.hurtAndBreak(PLAYER_USE_COST, player, broken -> broken.broadcastBreakEvent(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        TooltipHelper.addShiftTooltip(tooltip,
                List.of(Component.translatable("item.touhou_little_maid_spell.staranchor_pearl.desc1")
                        .withStyle(ChatFormatting.GRAY)),
                List.of(
                        Component.translatable("item.touhou_little_maid_spell.staranchor_pearl.desc2",
                                        PLAYER_USE_COST, VOID_WALK_TICKS / 20)
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.translatable("item.touhou_little_maid_spell.staranchor_pearl.desc3",
                                        MAID_BLOCK_COST)
                                .withStyle(ChatFormatting.DARK_PURPLE),
                        Component.translatable("item.touhou_little_maid_spell.staranchor_pearl.desc4")
                                .withStyle(ChatFormatting.YELLOW)));
    }
}
