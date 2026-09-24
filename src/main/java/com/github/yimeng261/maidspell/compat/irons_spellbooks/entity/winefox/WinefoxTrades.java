package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 星之魔女酒狐的交易表，按《NPC交易栏》分两档：
 *
 * <ul>
 *   <li><b>战胜前</b>——只做补给生意：五档墨水、几样吃的、星荧花簇。</li>
 *   <li><b>战胜后</b>——同一批补给半价，另外开放传说墨水、星锚珍珠、她的三件装备，
 *       以及她自己那四张一级卷轴。</li>
 * </ul>
 *
 * <p>「战胜前／后」这一档就是 {@code MagicalWinefoxBossEntity} 的 {@code tradingUnlocked}：
 * 它只在<b>合格</b>的一胜（没有女仆代打、没用真伤收尾）时置位，见
 * {@code MagicalWinefoxBossEntity#beginDefeat}。
 *
 * <p>补给那一列在文档里是「半价折扣后」逐行标出来的，所以这里写成
 * {@code discounted ? 半价 : 全价} 一次配平 —— 数字只留一份，改价不会只改一边。
 * 唯一例外是发光浆果：它连份量都在折扣档里从 10 掉到 8。
 */
public final class WinefoxTrades {

    /**
     * 卷轴上那一发法术的等级。
     *
     * <p>文档写的是「1级……卷轴」，与她自己在擂台上放的那一级一致。
     */
    private static final int SCROLL_LEVEL = 1;

    /** 卖出前先要付的绿宝石数，以及战胜后那个半价。 */
    private static final int COMMON_INK_PRICE = 6;
    private static final int UNCOMMON_INK_PRICE = 16;
    private static final int RARE_INK_PRICE = 26;
    private static final int EPIC_INK_PRICE = 40;
    private static final int SNACK_PRICE = 2;
    private static final int STAR_GLOW_PRICE = 1;

    private WinefoxTrades() {
    }

    /**
     * @param equipmentUnlocked 是否曾经取得过合格的胜利
     */
    public static MerchantOffers build(boolean equipmentUnlocked) {
        MerchantOffers offers = new MerchantOffers();
        addSupplies(offers, equipmentUnlocked);
        if (equipmentUnlocked) {
            addUnlocked(offers);
        }
        return offers;
    }

    /** 补给：战胜前全价，战胜后一律半价。 */
    private static void addSupplies(MerchantOffers offers, boolean discounted) {
        offers.add(offer(emeralds(half(COMMON_INK_PRICE, discounted)),
                new ItemStack(ItemRegistry.INK_COMMON.get())));
        offers.add(offer(emeralds(half(UNCOMMON_INK_PRICE, discounted)),
                new ItemStack(ItemRegistry.INK_UNCOMMON.get())));
        offers.add(offer(emeralds(half(RARE_INK_PRICE, discounted)),
                new ItemStack(ItemRegistry.INK_RARE.get())));
        offers.add(offer(emeralds(half(EPIC_INK_PRICE, discounted)),
                new ItemStack(ItemRegistry.INK_EPIC.get())));
        offers.add(offer(emeralds(half(SNACK_PRICE, discounted)),
                new ItemStack(Items.GOLDEN_CARROT)));
        // 折扣档里发光浆果是 8 个而不是 10 个，只有这一条的份量跟着价格一起变。
        offers.add(offer(emeralds(half(SNACK_PRICE, discounted)),
                new ItemStack(Items.GLOW_BERRIES, discounted ? 8 : 10)));
        offers.add(offer(emeralds(half(SNACK_PRICE, discounted)),
                new ItemStack(MaidSpellItems.YUE_LINGLAN.get())));
        offers.add(offer(emeralds(half(SNACK_PRICE, discounted)),
                new ItemStack(MaidSpellItems.JINGXU_YOULAN.get())));
        offers.add(offer(emeralds(STAR_GLOW_PRICE),
                new ItemStack(MaidSpellItems.STAR_GLOW_FLOWER_CLUSTER.get(), 4)));
    }

    /** 战胜后才摆出来的那一档。 */
    private static void addUnlocked(MerchantOffers offers) {
        offers.add(offer(emeralds(50), new ItemStack(ItemRegistry.INK_LEGENDARY.get())));
        offers.add(offer(emeralds(16), new ItemStack(MaidSpellItems.STARANCHOR_PEARL.get())));
        offers.add(offer(emeralds(32), new ItemStack(IronsSpellbooksCompatItems.STAR_WITCH_HAT.get())));
        offers.add(offer(emeralds(16), new ItemStack(MaidSpellItems.NEBULA_CORE.get()),
                new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_STAFF.get())));
        offers.add(offer(emeralds(16), new ItemStack(MaidSpellItems.NEBULA_CORE.get()),
                new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_LONGSWORD.get())));
        // 四张卷轴都是她自己在擂台上用的那几发：逐星飞瀑、星隙闪袭、星影斩击、剑牢。
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get())));
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.MODIFIED_TELEPORT.get())));
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.STAR_SHADOW_STRIKE.get())));
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.SWORD_PRISON.get())));
    }

    /**
     * 折扣价一律按 {@code (价格 + 1) / 2} 向上取整。
     *
     * <p>文档的折扣档里，卓越/稀有/史诗三档写的正是 8/13/20 —— 全价 16/26/40 的一半；
     * 只有普通墨水那一行印着 6（同时也是全价），同一行却标着「半价折扣后」。
     * 按「全档减半」这条唯一说得通的规则算，它是 3。
     */
    private static int half(int price, boolean discounted) {
        return discounted ? (price + 1) / 2 : price;
    }

    private static ItemStack emeralds(int count) {
        return new ItemStack(Items.EMERALD, count);
    }

    private static ItemStack scroll(AbstractSpell spell) {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, SCROLL_LEVEL, scroll);
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
