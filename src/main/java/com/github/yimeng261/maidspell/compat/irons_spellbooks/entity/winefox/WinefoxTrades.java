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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.ItemHandlerHelper;

/** 初始供应补给和日记兑换；合格胜利后永久解锁装备与卷轴。 */
public final class WinefoxTrades {

    /** 卷轴上那一发星陨的等级。她自己放的就是这个法术。 */
    private static final int STARFALL_SCROLL_LEVEL = 3;

    private WinefoxTrades() {
    }

    /**
     * @param equipmentUnlocked 是否曾经取得过合格的胜利
     */
    public static MerchantOffers build(boolean equipmentUnlocked) {
        MerchantOffers offers = new MerchantOffers();

        offers.add(offer(new ItemStack(Items.EMERALD, 24),
                new ItemStack(MaidSpellItems.STAR_GLOW_FLOWER_CLUSTER.get(), 4)));
        offers.add(offer(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.ENDER_PEARL, 4)));
        offers.add(offer(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.GOLDEN_CARROT, 8)));

        if (equipmentUnlocked) {
            offers.add(offer(new ItemStack(Items.EMERALD, 32), new ItemStack(Items.AMETHYST_SHARD, 8),
                new ItemStack(IronsSpellbooksCompatItems.STAR_WITCH_HAT.get())));
            offers.add(offer(new ItemStack(Items.EMERALD, 40), starfallScroll()));
            offers.add(offer(new ItemStack(Items.EMERALD, 40), scroll(IronsSpellbooksCompatSpells.MODIFIED_TELEPORT.get())));
            offers.add(offer(new ItemStack(Items.EMERALD, 40), scroll(IronsSpellbooksCompatSpells.SWORD_PRISON.get())));
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
        return scroll(IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get());
    }

    private static ItemStack scroll(io.redspace.ironsspellbooks.api.spells.AbstractSpell spell) {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(
                spell, STARFALL_SCROLL_LEVEL, scroll);
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
        return new MerchantOffer(costA, costB, result, Integer.MAX_VALUE, 0, 0.0F);
    }

    public static boolean isTravelDiary(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && stack.hasTag()
            && stack.getTag().getBoolean("touhou_little_maid_spell:travel_diary");
    }

    // Different chapters cannot stack in merchant input slots; accept any four carried diaries.
    public static void exchangeDiaries(Player player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isTravelDiary(stack)) count += stack.getCount();
        }
        if (count < 4) {
            player.displayClientMessage(Component.translatable(
                "dialogue.touhou_little_maid_spell.winefox.diaries_missing"), false);
            return;
        }
        int remaining = 4;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isTravelDiary(stack)) {
                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                remaining -= consumed;
            }
        }
        player.getInventory().setChanged();
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(MaidSpellItems.STAR_METEORITE.get()));
        player.displayClientMessage(Component.translatable(
            "dialogue.touhou_little_maid_spell.winefox.diaries_exchanged"), false);
    }
}
