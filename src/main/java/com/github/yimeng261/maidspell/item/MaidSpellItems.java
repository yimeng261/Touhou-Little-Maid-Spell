package com.github.yimeng261.maidspell.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.bauble.bleedingHeart.BleedingHeart;
import com.github.yimeng261.maidspell.item.bauble.blueNote.BlueNote;
import com.github.yimeng261.maidspell.item.bauble.chaosBook.ChaosBook;
import com.github.yimeng261.maidspell.item.bauble.doubleHeartChain.DoubleHeartChain;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocket;
import com.github.yimeng261.maidspell.item.bauble.flowCore.FlowCore;
import com.github.yimeng261.maidspell.item.bauble.hairpin.Hairpin;
import com.github.yimeng261.maidspell.item.bauble.quickChantRing.QuickChantRing;
import com.github.yimeng261.maidspell.item.bauble.rockCrystal.RockCrystal;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercis;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBook;
import com.github.yimeng261.maidspell.item.bauble.spellCore.SpellEnhancementCore;
import com.github.yimeng261.maidspell.item.bauble.springRing.SpringRing;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBlade;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCore;
import com.github.yimeng261.maidspell.item.common.WindSeekingBell.WindSeekingBell;
import com.github.yimeng261.maidspell.item.common.OwnerClearTool;
import com.github.yimeng261.maidspell.item.common.WindSeekingBell.WindSeekingBell;
import net.minecraft.Util;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 女仆法术饰品物品注册
 */
public class MaidSpellItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidSpellMod.MOD_ID);

    public static final DeferredItem<Item> SPELL_ENHANCEMENT_CORE = ITEMS.register("spell_enhancement_core", SpellEnhancementCore::new);
    public static final DeferredItem<Item> BLEEDING_HEART = ITEMS.register("bleeding_heart", BleedingHeart::new);
    public static final DeferredItem<Item> FLOW_CORE = ITEMS.register("flow_core", FlowCore::new);
    public static final DeferredItem<Item> QUICK_CHANT_RING = ITEMS.register("quick_chant_ring", QuickChantRing::new);
    public static final DeferredItem<Item> SPRING_RING = ITEMS.register("spring_ring", SpringRing::new);
    public static final DeferredItem<Item> BLUE_NOTE = ITEMS.register("blue_note", BlueNote::new);

    // 新增饰品
    public static final DeferredItem<Item> DOUBLE_HEART_CHAIN = ITEMS.register("double_heart_chain", DoubleHeartChain::new);
    public static final DeferredItem<Item> ROCK_CRYSTAL = ITEMS.register("rock_crystal", RockCrystal::new);
    public static final DeferredItem<Item> SLIVER_CERCIS = ITEMS.register("sliver_cercis", SilverCercis::new);
    public static final DeferredItem<Item> HAIRPIN = ITEMS.register("hairpin", Hairpin::new);
    public static final DeferredItem<Item> CHAOS_BOOK = ITEMS.register("chaos_book", ChaosBook::new);
    public static final DeferredItem<Item> SOUL_BOOK = ITEMS.register("soul_book", SoulBook::new);
    public static final DeferredItem<Item> ENDER_POCKET = ITEMS.register("ender_pocket", EnderPocket::new);
    public static final DeferredItem<Item> WOUND_RIME_BLADE = ITEMS.register("wound_rime_blade", WoundRimeBlade::new);
    public static final DeferredItem<Item> ANCHOR_CORE = ITEMS.register("anchor_core", AnchorCore::new);

    // 寻风之铃
    public static final DeferredItem<Item> WIND_SEEKING_BELL = ITEMS.register("wind_seeking_bell", WindSeekingBell::new);

    // 管理员工具
    public static final DeferredItem<Item> OWNER_CLEAR_TOOL = ITEMS.register("owner_clear_tool", OwnerClearTool::new);

    /**
     * 注册物品
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static String itemDesc(DeferredItem<Item> item) {
        return Util.makeDescriptionId("item", item.getId());
    }

}