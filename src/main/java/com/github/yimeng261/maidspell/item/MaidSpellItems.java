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
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogNecklace;
import com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity.FragrantIngenuity;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocket;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBlade;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCore;
import com.github.yimeng261.maidspell.item.bauble.spellOverlimitCore.SpellOverlimitCore;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystal;
import com.github.yimeng261.maidspell.item.common.WindSeekingBell.WindSeekingBell;
import com.github.yimeng261.maidspell.item.common.OwnerClearTool;
import com.github.yimeng261.maidspell.item.taskIcon.MeleeTaskIcon;
import com.github.yimeng261.maidspell.item.taskIcon.FarTaskIcon;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import z1gned.goetyrevelation.item.ModItems;

import java.lang.reflect.Field;

/**
 * 女仆法术饰品物品注册
 */
public class MaidSpellItems {

    private static volatile boolean goetyItemsResolved = false;
    private static Item cachedUnholyHat;
    private static Item cachedUnholyHatHalo;

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
    public static final RegistryObject<Item> TRANSMOG_NECKLACE = ITEMS.register("transmog_necklace", TransmogNecklace::new);
    public static final RegistryObject<Item> CHAOS_BOOK = ITEMS.register("chaos_book", ChaosBook::new);
    public static final RegistryObject<Item> SOUL_BOOK = ITEMS.register("soul_book", SoulBook::new);
    public static final RegistryObject<Item> ENDER_POCKET = ITEMS.register("ender_pocket", EnderPocket::new);
    public static final RegistryObject<Item> WOUND_RIME_BLADE = ITEMS.register("wound_rime_blade", WoundRimeBlade::new);
    public static final RegistryObject<Item> ANCHOR_CORE = ITEMS.register("anchor_core", AnchorCore::new);
    public static final RegistryObject<Item> SPELL_OVERLIMIT_CORE = ITEMS.register("spell_overlimit_core", SpellOverlimitCore::new);
    public static final RegistryObject<Item> FRAGRANT_INGENUITY = ITEMS.register("fragrant_ingenuity", FragrantIngenuity::new);
    //public static final RegistryObject<Item> CRYSTAL_CIRCUIT = ITEMS.register("crystal_circuit", CrystalCircuit::new);

    // 梦云水晶
    public static final RegistryObject<Item> DREAM_CAT_CRYSTAL = ITEMS.register("dream_cat_crystal", DreamCatCrystal::new);

    // 寻风之铃
    public static final RegistryObject<Item> WIND_SEEKING_BELL = ITEMS.register("wind_seeking_bell", WindSeekingBell::new);
    
    // 管理员工具
    public static final RegistryObject<Item> OWNER_CLEAR_TOOL = ITEMS.register("owner_clear_tool", OwnerClearTool::new);

    // 任务图标物品
    public static final RegistryObject<Item> MELEE_TASK_ICON = ITEMS.register("melee_task_icon", MeleeTaskIcon::new);
    public static final RegistryObject<Item> FAR_TASK_ICON = ITEMS.register("far_task_icon", FarTaskIcon::new);

    /**
     * 注册物品
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static String itemDesc(RegistryObject<Item> item) {
        return Util.makeDescriptionId("item", item.getId());
    }

    /**
     * 获取原版 Goety Revelation 模组的晋升之环
     * 如果模组未加载，返回 null
     */
    public static Item getAscensionHalo() {
        if (ModList.get().isLoaded("goety_revelation")) {
            try {
                return ModItems.ASCENSION_HALO.get();
            } catch (Exception e) {
                // 模组加载但物品不存在
                return null;
            }
        }
        return null;
    }

    /**
     * 获取原版 Goety 模组的不洁圣冠
     * 如果模组未加载，返回 null
     */
    public static Item getUnholyHat() {
        ensureGoetyItemsResolved();
        return cachedUnholyHat;
    }

    /**
     * 获取原版 Goety 模组的不洁圣冠光环（另一种皮肤）
     * 如果模组未加载，返回 null
     */
    public static Item getUnholyHatHalo() {
        ensureGoetyItemsResolved();
        return cachedUnholyHatHalo;
    }

    private static void ensureGoetyItemsResolved() {
        if (goetyItemsResolved) {
            return;
        }

        synchronized (MaidSpellItems.class) {
            if (goetyItemsResolved) {
                return;
            }

            if (ModList.get().isLoaded("goety")) {
                cachedUnholyHat = resolveOptionalGoetyItem("unholy_hat", "UNHOLY_HAT");
                cachedUnholyHatHalo = resolveOptionalGoetyItem("unholy_hat_halo", "UNHOLY_HAT_HALO");
            }

            goetyItemsResolved = true;
        }
    }

    private static Item resolveOptionalGoetyItem(String itemId, String fieldName) {
        Item itemFromRegistry = ForgeRegistries.ITEMS.getValue(new ResourceLocation("goety", itemId));
        if (itemFromRegistry != null) {
            return itemFromRegistry;
        }

        try {
            Class<?> modItemsClass = Class.forName("com.Polarice3.Goety.common.items.ModItems");
            Field field = modItemsClass.getField(fieldName);
            Object value = field.get(null);

            if (value instanceof RegistryObject<?> registryObject) {
                Object item = registryObject.get();
                if (item instanceof Item resolvedItem) {
                    return resolvedItem;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 旧版 Goety 可能没有该字段，直接忽略即可
        }

        return null;
    }

} 
