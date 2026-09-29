package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowSpearItem;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarWitchHatItem;
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

    public static final DeferredItem<Item> HOLY_CONSTRUCT_SPAWN_EGG =
            ITEMS.register("holy_construct_spawn_egg",
                    () -> new DeferredSpawnEggItem(IronsSpellbooksCompatEntities.HOLY_CONSTRUCT,
                            0xF5EBC7,
                            0xFFD54F,
                            new Item.Properties()));

    // 万法酒狐佩戴的星之魔女系列装备
    public static final DeferredItem<Item> STAR_SHADOW_LONGSWORD =
            ITEMS.register("star_shadow_longsword", StarShadowLongswordItem::new);

    public static final DeferredItem<Item> STAR_SHADOW_STAFF =
            ITEMS.register("star_shadow_staff", StarShadowStaffItem::new);

    public static final DeferredItem<Item> STAR_WITCH_HAT =
            ITEMS.register("star_witch_hat", StarWitchHatItem::new);

    // 星影投枪：星之魔女的投枪，玩家拿到后按三叉戟使用
    public static final DeferredItem<Item> STAR_SHADOW_SPEAR =
            ITEMS.register("star_shadow_spear", StarShadowSpearItem::new);

    private IronsSpellbooksCompatItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
