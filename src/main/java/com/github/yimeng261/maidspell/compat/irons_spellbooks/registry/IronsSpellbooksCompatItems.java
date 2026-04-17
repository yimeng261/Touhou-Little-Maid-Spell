package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IronsSpellbooksCompatItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidSpellMod.MOD_ID);

    public static final DeferredItem<Item> CORRUPTED_KNIGHT_SPAWN_EGG =
            ITEMS.register("corrupted_knight_spawn_egg",
                    () -> new DeferredSpawnEggItem(IronsSpellbooksCompatEntities.CORRUPTED_KNIGHT,
                            0x5B0F18,
                            0xC9B6A5,
                            new Item.Properties()));

    public static final DeferredItem<Item> SHADOW_ASSASSIN_SPAWN_EGG =
            ITEMS.register("shadow_assassin_spawn_egg",
                    () -> new DeferredSpawnEggItem(IronsSpellbooksCompatEntities.SHADOW_ASSASSIN,
                            0x231B2E,
                            0x9C7BFF,
                            new Item.Properties()));

    public static final DeferredItem<Item> ELF_TEMPLAR_SPAWN_EGG =
            ITEMS.register("elf_templar_spawn_egg",
                    () -> new DeferredSpawnEggItem(IronsSpellbooksCompatEntities.ELF_TEMPLAR,
                            0x5A7B4B,
                            0xD9E8B5,
                            new Item.Properties()));

    private IronsSpellbooksCompatItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
