package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class IronsSpellbooksCompatItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidSpellMod.MOD_ID);

    public static final RegistryObject<Item> CORRUPTED_KNIGHT_SPAWN_EGG =
            ITEMS.register("corrupted_knight_spawn_egg",
                    () -> new ForgeSpawnEggItem(IronsSpellbooksCompatEntities.CORRUPTED_KNIGHT,
                            0x5B0F18,
                            0xC9B6A5,
                            new Item.Properties()));

    public static final RegistryObject<Item> SHADOW_ASSASSIN_SPAWN_EGG =
            ITEMS.register("shadow_assassin_spawn_egg",
                    () -> new ForgeSpawnEggItem(IronsSpellbooksCompatEntities.SHADOW_ASSASSIN,
                            0x231B2E,
                            0x9C7BFF,
                            new Item.Properties()));

    public static final RegistryObject<Item> ELF_TEMPLAR_SPAWN_EGG =
            ITEMS.register("elf_templar_spawn_egg",
                    () -> new ForgeSpawnEggItem(IronsSpellbooksCompatEntities.ELF_TEMPLAR,
                            0x5A7B4B,
                            0xD9E8B5,
                            new Item.Properties()));

    public static final RegistryObject<Item> HOLY_CONSTRUCT_SPAWN_EGG =
            ITEMS.register("holy_construct_spawn_egg",
                    () -> new ForgeSpawnEggItem(IronsSpellbooksCompatEntities.HOLY_CONSTRUCT,
                            0xF5EBC7,
                            0xFFD54F,
                            new Item.Properties()));

    private IronsSpellbooksCompatItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
