package com.github.yimeng261.maidspell.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 女仆法术饰品物品注册
 * 只有在铁魔法模组加载时才会注册这些物品
 */
public class MaidSpellItems {
    
    // 只有当铁魔法模组存在时才创建注册器
    private static final DeferredRegister<Item> ITEMS = ModList.get().isLoaded("irons_spellbooks") 
        ? DeferredRegister.create(ForgeRegistries.ITEMS, "touhou_little_maid_spell")
        : null;
    
    // 法术强化核心 - 主饰品
    public static final RegistryObject<Item> SPELL_ENHANCEMENT_CORE = ITEMS != null 
        ? ITEMS.register("spell_enhancement_core", SpellEnhancementCore::new)
        : null;
    
    /**
     * 注册物品（只在铁魔法模组存在时执行）
     */
    public static void register(IEventBus eventBus) {
        if (ITEMS != null) {
            ITEMS.register(eventBus);
        }
    }
    
    /**
     * 检查铁魔法模组是否已加载
     */
    public static boolean isIronsSpellbooksLoaded() {
        return ModList.get().isLoaded("irons_spellbooks");
    }
} 