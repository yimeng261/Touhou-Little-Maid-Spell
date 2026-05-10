package com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MaidSpellContainers {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, "touhou_little_maid_spell");

    public static final Supplier<MenuType<SpellWhiteListContainer>> SPELL_WHITE_LIST_CONTAINER =
        MENU_TYPES.register("blue_note_container", () -> SpellWhiteListContainer.TYPE);

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
