package com.github.yimeng261.maidspell.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.bauble.bleedingHeart.BleedingHeart;
import com.github.yimeng261.maidspell.item.bauble.blueNote.BlueNote;
import com.github.yimeng261.maidspell.item.bauble.doubleHeartChain.DoubleHeartChain;
import com.github.yimeng261.maidspell.item.bauble.flowCore.FlowCore;
import com.github.yimeng261.maidspell.item.bauble.quickChantRing.QuickChantRing;
import com.github.yimeng261.maidspell.item.bauble.rockCrystal.RockCrystal;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercis;
import com.github.yimeng261.maidspell.item.bauble.spellCore.SpellEnhancementCore;
import com.github.yimeng261.maidspell.item.bauble.springRing.SpringRing;
import com.github.yimeng261.maidspell.item.bauble.hairpin.Hairpin;
import com.github.yimeng261.maidspell.item.bauble.chaosBook.ChaosBook;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBook;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocket;
import net.minecraft.Util;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 女仆法术饰品物品注册
 * 只有在铁魔法模组加载时才会注册这些物品
 */
public class MaidSpellItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MaidSpellMod.MOD_ID);

    public static final RegistryObject<Item> SPELL_ENHANCEMENT_CORE = ITEMS.register("spell_enhancement_core", SpellEnhancementCore::new);
    public static final RegistryObject<Item> BLEEDING_HEART = ITEMS.register("bleeding_heart", BleedingHeart::new);
    public static final RegistryObject<Item> FLOW_CORE = ITEMS.register("flow_core", FlowCore::new);
    public static final RegistryObject<Item> QUICK_CHANT_RING = ITEMS.register("quick_chant_ring", QuickChantRing::new);
    public static final RegistryObject<Item> SPRING_RING = ITEMS.register("spring_ring", SpringRing::new);
    public static final RegistryObject<Item> BLUE_NOTE = ITEMS.register("blue_note", BlueNote::new);
    
    // 新增饰品
    public static final RegistryObject<Item> DOUBLE_HEART_CHAIN = ITEMS.register("double_heart_chain", DoubleHeartChain::new);
    public static final RegistryObject<Item> ROCK_CRYSTAL = ITEMS.register("rock_crystal", RockCrystal::new);
    public static final RegistryObject<Item> SLIVER_CERCIS = ITEMS.register("sliver_cercis", SilverCercis::new);
    public static final RegistryObject<Item> HAIRPIN = ITEMS.register("hairpin", Hairpin::new);
    public static final RegistryObject<Item> CHAOS_BOOK = ITEMS.register("chaos_book", ChaosBook::new);
    public static final RegistryObject<Item> SOUL_BOOK = ITEMS.register("soul_book", SoulBook::new);
    public static final RegistryObject<Item> ENDER_POCKET = ITEMS.register("ender_pocket", EnderPocket::new);

    /**
     * 注册物品
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static String itemDesc(RegistryObject<Item> item) {
        return Util.makeDescriptionId("item", item.getId());
    }

} 