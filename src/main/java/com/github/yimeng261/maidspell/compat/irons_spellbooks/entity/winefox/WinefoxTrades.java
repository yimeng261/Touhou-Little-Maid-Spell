package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 酒狐交易分为战胜前补给和合格胜利后解锁的商品。
 * 解锁后补给降价；每日限购由 {@link DailyQuotaTable} 管理。
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

    /**
     * 每日限购次数，按《NPC交易栏》那一列抄：三件装备各 1 件，星锚珍珠与传说墨水各 3 件。
     *
     * <p>每条报价的上限是各自写在 {@code maxUses} 上的 —— 一张表里三种上限（1／3／不限）并存，
     * 所以不能拿一个表级常量统一盖。
     */
    private static final int GEAR_DAILY_LIMIT = 1;
    private static final int KEY_ITEM_DAILY_LIMIT = 3;

    /** 一整天。跨过这么多游戏刻、或者世界时间翻过一天，就把限购次数清零，见 {@link DailyQuotaTable}。 */
    private static final long DAY_TICKS = 24000L;

    private WinefoxTrades() {
    }

    /**
     * @param equipmentUnlocked 是否曾经取得过合格的胜利
     */
    public static MerchantOffers build(boolean equipmentUnlocked) {
        return new DailyQuotaTable(equipmentUnlocked);
    }

    /** 只造报价，不管跨天：跨天那件事由 {@link DailyQuotaTable} 在问价前统一处理。 */
    private static MerchantOffers rows(boolean equipmentUnlocked) {
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
        offers.add(offer(emeralds(50), KEY_ITEM_DAILY_LIMIT, new ItemStack(ItemRegistry.INK_LEGENDARY.get())));
        offers.add(offer(emeralds(16), KEY_ITEM_DAILY_LIMIT, new ItemStack(MaidSpellItems.STARANCHOR_PEARL.get())));
        offers.add(offer(emeralds(32), GEAR_DAILY_LIMIT, new ItemStack(IronsSpellbooksCompatItems.STAR_WITCH_HAT.get())));
        offers.add(offer(emeralds(16), new ItemStack(MaidSpellItems.NEBULA_CORE.get()),
                new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_STAFF.get()), GEAR_DAILY_LIMIT));
        offers.add(offer(emeralds(16), new ItemStack(MaidSpellItems.NEBULA_CORE.get()),
                new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_LONGSWORD.get()), GEAR_DAILY_LIMIT));
        // 四张卷轴都是她自己在擂台上用的那几发：逐星飞瀑、星隙闪袭、星影斩击、星影剑阵（原「剑牢」）。
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get())));
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.MODIFIED_TELEPORT.get())));
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.STAR_SHADOW_STRIKE.get())));
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.SWORD_PRISON.get())));
        // 两发后加的：三矢连星与伴星黑洞，同样 1 级、同样 10 绿宝石。
        // 它们**不**实现 BossExclusiveSpell —— 这里就是在卖，玩家拿到卷轴是设计的一部分。
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.TRIPLE_STAR_ARROW.get())));
        offers.add(offer(emeralds(10), scroll(IronsSpellbooksCompatSpells.COMPANION_BLACK_HOLE.get())));
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

    /** 不限购的行：左上角小字那一列（补给与卷轴）。 */
    private static MerchantOffer offer(ItemStack cost, ItemStack result) {
        return offer(cost, ItemStack.EMPTY, result, Integer.MAX_VALUE);
    }

    private static MerchantOffer offer(ItemStack cost, int dailyLimit, ItemStack result) {
        return offer(cost, ItemStack.EMPTY, result, dailyLimit);
    }

    /** maxUses 表示每日限额；初始 uses、经验和动态调价均为零。 */
    private static MerchantOffer offer(ItemStack costA, ItemStack costB, ItemStack result, int maxUses) {
        return new MerchantOffer(costA, costB, result, 0, maxUses, 0, 0.0F);
    }

    /**
     * 按游戏时间或世界时间跨天重置限购次数。
     * 原位重置报价，避免交易槽仍引用旧对象；Boss 会将使用次数存入 NBT，
     * 读档后由 {@link #rebuildKeepingUses} 恢复。
     */
    private static final class DailyQuotaTable extends MerchantOffers {

        /** 上一次清零时的世界游戏时间与世界时间；负数表示这张表刚造出来、还没记过。 */
        private long refreshedGameTime = -1L;
        private long refreshedDayTime = -1L;

        private DailyQuotaTable(boolean equipmentUnlocked) {
            this(rows(equipmentUnlocked));
        }

        /**
         * 用一张现成的报价表包一层。
         *
         * <p>读档走这一支：存档里那份表是原版 {@code MerchantOffers} 反序列化出来的
         * （行是普通 {@code MerchantOffer}），直接挂上去就没有跨天清零了 ——
         * 所以必须重新包一次，把「次数怎么算」这件事粘回去，而不是只把数据搬回来。
         */
        private DailyQuotaTable(List<MerchantOffer> source) {
            addAll(source);
        }

        @Override
        public MerchantOffer getRecipeFor(ItemStack costA, ItemStack costB, int hint) {
            refreshIfNewDay();
            return super.getRecipeFor(costA, costB, hint);
        }

        @Override
        public void writeToStream(FriendlyByteBuf buffer) {
            // 开交易栏时服务端把整张表现发一份给客户端，顺手在这里也过一遍：
            // 客户端手里只有快照，不在这儿刷一下，跨天之后界面上那一行还画着红叉（判定在服务端，红叉只是难看）。
            refreshIfNewDay();
            super.writeToStream(buffer);
        }

        private void refreshIfNewDay() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                // 没有服务器实例（数据生成、结构校验，或理论上不该出现的客户端侧调用）：
                // 拿不到世界时间就别动，宁可多留一次限购，也不能凭空把次数抹掉。
                return;
            }
            ServerLevel overworld = server.overworld();
            long gameTime = overworld.getGameTime();
            long dayTime = overworld.getDayTime();
            if (this.refreshedGameTime < 0L) {
                stamp(gameTime, dayTime);
                return;
            }
            boolean dayElapsed = gameTime - this.refreshedGameTime >= DAY_TICKS;
            boolean worldDayRolled = dayTime / DAY_TICKS > this.refreshedDayTime / DAY_TICKS;
            if (!dayElapsed && !worldDayRolled) {
                return;
            }
            for (MerchantOffer offer : this) {
                offer.resetUses();
            }
            stamp(gameTime, dayTime);
        }

        private void stamp(long gameTime, long dayTime) {
            this.refreshedGameTime = gameTime;
            this.refreshedDayTime = dayTime;
        }
    }

    /**
     * 按当前代码重建报价并包回每日限购表，让旧存档获得新增商品。
     * 按结果和代价匹配旧报价，保留使用次数而不依赖行序。
     */
    public static MerchantOffers rebuildKeepingUses(MerchantOffers saved, boolean equipmentUnlocked) {
        List<MerchantOffer> rebuilt = new ArrayList<>();
        // rows() 返回的就是 MerchantOffers（本身是 List<MerchantOffer>），不必再包一层。
        for (MerchantOffer fresh : rows(equipmentUnlocked)) {
            MerchantOffer previous = findMatching(saved, fresh);
            if (previous == null) {
                rebuilt.add(fresh);
                continue;
            }
            // 1.20.1 的 MerchantOffer <b>没有</b> setUses —— 次数只能在构造时给。
            // 所以这里新造一个同样报价、但把 uses / demand 搬过来的对象（7 参构造的最后一位是 demand）。
            // 不搬 demand 会让价格随卖出次数浮动，而她这张表原先是不浮动的（priceMultiplier 恒 0）。
            rebuilt.add(new MerchantOffer(
                    fresh.getBaseCostA(), fresh.getCostB(), fresh.getResult(),
                    Math.min(previous.getUses(), fresh.getMaxUses()),
                    fresh.getMaxUses(), 0, 0.0F, previous.getDemand()));
        }
        return new DailyQuotaTable(rebuilt);
    }

    @Nullable
    private static MerchantOffer findMatching(MerchantOffers saved, MerchantOffer target) {
        if (saved == null) {
            return null;
        }
        for (MerchantOffer candidate : saved) {
            if (ItemStack.isSameItemSameTags(candidate.getResult(), target.getResult())
                && ItemStack.isSameItemSameTags(candidate.getCostA(), target.getCostA())
                && ItemStack.isSameItemSameTags(candidate.getCostB(), target.getCostB())) {
                return candidate;
            }
        }
        return null;
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
