package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;

/**
 * 观星术士的交易表，照《NPC交易栏》抄：一半是「购入」（她花绿宝石收玩家的东西），
 * 一半是「出售」（玩家花绿宝石买她的货）。
 *
 * <p>与星之魔女酒狐那两张表不同，她的报价是<b>有偿</b>的：每条每天 5 次、半天补一次货。
 * 补货那套机器在 {@code GuardianWitchEntity} 上（{@code IMerchantWizard}），
 * 与 {@code ElfTemplarEntity} 同一份规则。
 */
public final class AstroMancerTrades {

    /** 卖出前先要付的绿宝石数。 */
    private static final int INK_COMMON_BUY_PRICE = 6;
    private static final int INK_UNCOMMON_BUY_PRICE = 15;
    private static final int INK_RARE_BUY_PRICE = 25;
    private static final int ARCANE_ESSENCE_BUY_PRICE = 1;
    private static final int FOOD_BUY_PRICE = 5;

    /** 卷轴上的那一发魔法霰弹的等级。 */
    private static final int SCROLL_LEVEL = 1;

    /**
     * 「原版食物」那一行的实体。
     *
     * <p>交易界面是按<b>具体物品</b>匹配的（{@code MerchantOffer.isRequiredItem} 比的是
     * {@code ItemStack}），原版与 Forge 也都没有一个能用的食物标签
     * （Forge 只有 {@code forge:crops} / {@code forge:eggs} / {@code forge:mushrooms}
     * 这类细分标签，没有笼统的 food），所以「任意原版食物」在交易栏里做不到。
     * 这里退一步，把最常拿来换钱的那些摊成几条报价 —— 想加别的往这个表里加一行即可。
     */
    private static final List<Item> VANILLA_FOODS = List.of(
            Items.BREAD,
            Items.COOKED_BEEF,
            Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN,
            Items.BAKED_POTATO,
            Items.APPLE);

    private AstroMancerTrades() {
    }

    public static MerchantOffers build() {
        MerchantOffers offers = new MerchantOffers();

        // 购入：左边是玩家给她的，右边是她付的绿宝石。
        offers.add(buy(new ItemStack(ItemRegistry.INK_COMMON.get()), INK_COMMON_BUY_PRICE));
        offers.add(buy(new ItemStack(ItemRegistry.INK_UNCOMMON.get()), INK_UNCOMMON_BUY_PRICE));
        offers.add(buy(new ItemStack(ItemRegistry.INK_RARE.get()), INK_RARE_BUY_PRICE));
        offers.add(buy(new ItemStack(ItemRegistry.ARCANE_ESSENCE.get()), ARCANE_ESSENCE_BUY_PRICE));
        for (Item food : VANILLA_FOODS) {
            offers.add(buy(new ItemStack(food), FOOD_BUY_PRICE));
        }

        // 出售：左边是玩家付的绿宝石，右边是她给的东西。
        offers.add(sell(16, new ItemStack(MaidSpellItems.STARANCHOR_PEARL.get())));
        offers.add(sell(40, new ItemStack(ItemRegistry.INK_EPIC.get())));
        offers.add(sell(5, new ItemStack(ItemRegistry.ENDER_RUNE.get())));
        offers.add(sell(16, new ItemStack(ItemRegistry.MITHRIL_SCRAP.get())));
        offers.add(sell(4, new ItemStack(Items.ENDER_PEARL)));
        offers.add(sell(10, scroll(IronsSpellbooksCompatSpells.MAGIC_SHOTGUN.get())));

        return offers;
    }

    private static MerchantOffer buy(ItemStack goods, int emeralds) {
        return offer(goods, new ItemStack(Items.EMERALD, emeralds));
    }

    private static MerchantOffer sell(int emeralds, ItemStack goods) {
        return offer(new ItemStack(Items.EMERALD, emeralds), goods);
    }

    private static ItemStack scroll(AbstractSpell spell) {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, SCROLL_LEVEL, scroll);
        return scroll;
    }

    /**
     * {@code maxUses} 由观星术士那边的每日限额决定，这里给的是「还没开张」的初值：
     * 真正的上限在 {@code GuardianWitchEntity#normalizeDailyUseLimit} 里统一盖成
     * {@code DAILY_TRADE_MAX_USES}，存档读回来也走同一处。
     */
    private static MerchantOffer offer(ItemStack cost, ItemStack result) {
        return new MerchantOffer(cost, ItemStack.EMPTY, result, 0,
                GuardianWitchEntity.DAILY_TRADE_MAX_USES, 1, 0.05F);
    }
}
