package com.github.yimeng261.maidspell.item.bauble.blueNote.contianer;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MaidSpellContainers {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = 
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, "touhou_little_maid_spell");
    
    public static final RegistryObject<MenuType<BlueNoteContainer>> BLUE_NOTE_CONTAINER = 
        MENU_TYPES.register("blue_note_container", () -> BlueNoteContainer.TYPE);
    
    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
} 