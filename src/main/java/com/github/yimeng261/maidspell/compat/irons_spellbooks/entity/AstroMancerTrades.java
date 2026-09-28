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
 * 观星术士的购入与出售报价。普通商品每日限购 5 次并补货，
 * 星锚珍珠每日 1 件，星陨石只出售一次。
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
     * 星锚珍珠的每日上限，按《NPC交易栏》那一列：1 件。
     *
     * <p>注意她的补货周期是<b>半天</b>（{@code IMerchantWizard#shouldRestock} 的 12000 tick 窗口，与
     * {@code ElfTemplarEntity} 同源），所以这一条实际是「每次补货 1 件、一天最多两次」。
     * 要严格一天一件，得把整台补货机器的窗口改成 24000 tick —— 那会一起改掉她所有报价，
     * 也会和精灵守卫分家，不在这里动。
     */
    private static final int STARANCHOR_PEARL_DAILY_LIMIT = 1;

    /** 只可交易一次的那一条（星陨石）用掉的次数上限，见 {@link #isOnceEver}。 */
    private static final int ONCE_EVER_USES = 1;

    /** 成交给的经验与要价系数：她不是村民，经验只走界面显示，需求涨跌不改价。 */
    private static final int XP_PER_TRADE = 1;
    private static final float PRICE_MULTIPLIER = 0.05F;

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
        // 星陨石：六十四颗绿宝石外加一颗下界之星，只此一次（补货机器不会把它刷回来，见 #isOnceEver）。
        // 两样付款走 costA／costB 那一对槽位，与酒狐那边换法杖／长剑的写法一致。
        offers.add(offer(new ItemStack(Items.EMERALD, 64), new ItemStack(Items.NETHER_STAR),
                new ItemStack(MaidSpellItems.STAR_METEORITE.get()), ONCE_EVER_USES));

        return offers;
    }

    private static MerchantOffer buy(ItemStack goods, int emeralds) {
        return offer(goods, ItemStack.EMPTY, new ItemStack(Items.EMERALD, emeralds));
    }

    private static MerchantOffer sell(int emeralds, ItemStack goods) {
        return offer(new ItemStack(Items.EMERALD, emeralds), ItemStack.EMPTY, goods);
    }

    private static ItemStack scroll(AbstractSpell spell) {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, SCROLL_LEVEL, scroll);
        return scroll;
    }

    /**
     * 每档报价的每日上限，按「她卖出去的东西」认行。
     *
     * <p>认东西而不是认报价对象：存档里那张表是从 NBT 里 {@code new MerchantOffer(tag)} 造回来的，
     * 任何自定义标记都过不了这一关，只有 {@code uses}／{@code maxUses}／物品本身留得下来。
     */
    public static int dailyLimitFor(ItemStack result) {
        if (isOnceEver(result)) {
            return ONCE_EVER_USES;
        }
        return result.is(MaidSpellItems.STARANCHOR_PEARL.get())
                ? STARANCHOR_PEARL_DAILY_LIMIT
                : GuardianWitchEntity.DAILY_TRADE_MAX_USES;
    }

    /**
     * 「只可交易一次」的那一行：星陨石。
     *
     * <p>它靠 {@code maxUses = 1} 加上「补货时跳过它」实现（见 {@code GuardianWitchEntity#restock}）：
     * {@code uses}／{@code maxUses} 都会随交易表落盘，所以读档回来仍是卖光，不会变成每天一颗。
     * 只要以后没有别的行卖星陨石，按东西认行就够用。
     */
    public static boolean isOnceEver(ItemStack result) {
        return result.is(MaidSpellItems.STAR_METEORITE.get());
    }

    /** 按当前报价重建旧存档交易表，按结果和代价匹配并保留使用次数。 */
    public static MerchantOffers rebuildKeepingUses(MerchantOffers saved) {
        MerchantOffers rebuilt = new MerchantOffers();
        for (MerchantOffer current : build()) {
            MerchantOffer previous = saved == null ? null : findSameRow(saved, current);
            rebuilt.add(new MerchantOffer(
                    current.getBaseCostA().copy(),
                    current.getCostB().copy(),
                    current.getResult().copy(),
                    previous == null ? 0 : Math.min(previous.getUses(), current.getMaxUses()),
                    current.getMaxUses(),
                    current.getXp(),
                    current.getPriceMultiplier(),
                    previous == null ? 0 : previous.getDemand()));
        }
        return rebuilt;
    }

    private static MerchantOffer findSameRow(MerchantOffers saved, MerchantOffer current) {
        for (MerchantOffer old : saved) {
            if (ItemStack.isSameItemSameTags(old.getResult(), current.getResult())
                    && ItemStack.isSameItemSameTags(old.getBaseCostA(), current.getBaseCostA())
                    && ItemStack.isSameItemSameTags(old.getCostB(), current.getCostB())) {
                return old;
            }
        }
        return null;
    }

    /**
     * {@code maxUses} 就是这一行的每日限额，见 {@link #dailyLimitFor}；第 4 个参数是「已经用掉几次」的初值，
     * 不是上限（{@code (costA, costB, result, uses, maxUses, xp, priceMultiplier)}）。
     */
    private static MerchantOffer offer(ItemStack costA, ItemStack costB, ItemStack result, int maxUses) {
        return new MerchantOffer(costA, costB, result, 0, maxUses, XP_PER_TRADE, PRICE_MULTIPLIER);
    }

    /** 走每日限额的那条路：上限按卖的东西现查。 */
    private static MerchantOffer offer(ItemStack costA, ItemStack costB, ItemStack result) {
        return offer(costA, costB, result, dailyLimitFor(result));
    }
}
