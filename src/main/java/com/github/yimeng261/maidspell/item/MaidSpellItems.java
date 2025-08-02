package com.github.yimeng261.maidspell.item;

import com.github.yimeng261.maidspell.item.bauble.bleedingHeart.BleedingHeart;
import com.github.yimeng261.maidspell.item.bauble.flowCore.FlowCore;
import com.github.yimeng261.maidspell.item.bauble.spellCore.SpellEnhancementCore;
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

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "touhou_little_maid_spell");

    public static final RegistryObject<Item> SPELL_ENHANCEMENT_CORE = ITEMS.register("spell_enhancement_core", SpellEnhancementCore::new);

    public static final RegistryObject<Item> BLEEDING_HEART = ITEMS.register("bleeding_heart", BleedingHeart::new);

    public static final RegistryObject<Item> FLOW_CORE = ITEMS.register("flow_core", FlowCore::new);
    
    /**
     * 注册物品
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

} 