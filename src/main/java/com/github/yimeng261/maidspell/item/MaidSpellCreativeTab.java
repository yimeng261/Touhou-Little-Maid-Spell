package com.github.yimeng261.maidspell.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 女仆法术模组的创造模式物品栏
 * 始终注册，但只有在铁魔法模组存在时才显示相关物品
 */
public class MaidSpellCreativeTab {
    
    // 始终创建注册器
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "touhou_little_maid_spell");
    
    public static final RegistryObject<CreativeModeTab> MAID_SPELL_TAB = 
        CREATIVE_MODE_TABS.register("maid_spell_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.touhou_little_maid_spell"))
            .icon(() -> new ItemStack(MaidSpellItems.BLEEDING_HEART.get()))
            .displayItems((parameters, output) -> {
                if (ModList.get().isLoaded("irons_spellbooks")) {
                    output.accept(MaidSpellItems.SPELL_ENHANCEMENT_CORE.get());
                }

                output.accept(MaidSpellItems.BLEEDING_HEART.get());
                output.accept(MaidSpellItems.FLOW_CORE.get());
            })
            .build());
    
    /**
     * 注册创造模式物品栏
     */
    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
} 