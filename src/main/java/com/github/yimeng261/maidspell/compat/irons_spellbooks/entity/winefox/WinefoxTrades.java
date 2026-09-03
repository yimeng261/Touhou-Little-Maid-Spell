package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * 战败之后万法酒狐开出的交易。
 *
 * <p>分两档：
 * <ul>
 *   <li><b>基础档</b>——只要她输过一次就有。卖装备和法术卷轴，这些东西日记里都提到过，
 *       算是把她留在路上的那些东西正式交到玩家手上。</li>
 *   <li><b>特殊档</b>——只在这一场没被判「女仆代打」时追加。星影长剑与星影法杖是她本人的武器，
 *       要拿得自己下场赢她。价码里那枚星云核心正是打赢时还回来的那一枚：
 *       <b>留着重挑战，还是换她的武器，二选一。</b></li>
 * </ul>
 *
 * <p>价格与货品是可调的默认值，不是什么平衡结论。真要动，动这一个文件就够。
 */
public final class WinefoxTrades {

    /** 卷轴上那一发星陨的等级。她自己放的就是这个法术。 */
    private static final int STARFALL_SCROLL_LEVEL = 3;

    private WinefoxTrades() {
    }

    /**
     * @param restricted 这一场被判了限制（女仆代打 / 用过真伤），只给基础档
     */
    public static MerchantOffers build(boolean restricted) {
        MerchantOffers offers = new MerchantOffers();

        offers.add(offer(new ItemStack(Items.EMERALD, 24),
                new ItemStack(MaidSpellItems.STAR_GLOW_FLOWER_CLUSTER.get(), 4)));
        offers.add(offer(new ItemStack(Items.EMERALD, 32), new ItemStack(Items.AMETHYST_SHARD, 8),
                new ItemStack(IronsSpellbooksCompatItems.STAR_WITCH_HAT.get())));
        offers.add(offer(new ItemStack(Items.EMERALD, 40), starfallScroll()));

        if (!restricted) {
            offers.add(offer(new ItemStack(MaidSpellItems.NEBULA_CORE.get()),
                    new ItemStack(Items.EMERALD, 16),
                    new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_LONGSWORD.get())));
            offers.add(offer(new ItemStack(MaidSpellItems.NEBULA_CORE.get()),
                    new ItemStack(Items.EMERALD, 16),
                    new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_STAFF.get())));
        }
        return offers;
    }

    /**
     * 一张她的星陨卷轴。
     *
     * <p>用的是本模组改过的那一版（{@code starfall_modified}）而不是铁魔法原版：
     * 玩家在擂台上挨的就是这一发，拿到手的自然也该是同一发。
     */
    private static ItemStack starfallScroll() {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(
                IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get(), STARFALL_SCROLL_LEVEL, scroll);
        return scroll;
    }

    private static MerchantOffer offer(ItemStack cost, ItemStack result) {
        return offer(cost, ItemStack.EMPTY, result);
    }

    /**
     * {@code maxUses} 给得很大、{@code xp} 给 0：她不是村民，没有等级也没有补货循环，
     * 交易表整个由 {@link #build} 按限制标志重算，不该出现"卖光了"这种状态。
     */
    private static MerchantOffer offer(ItemStack costA, ItemStack costB, ItemStack result) {
        return new MerchantOffer(costA, costB, result, Integer.MAX_VALUE, 0, 1.0F);
    }
}
