package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarWitchHatItem;
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

    public static final RegistryObject<Item> GUARDIAN_WITCH_SPAWN_EGG =
            ITEMS.register("astro_mancer_spawn_egg",
                    () -> new ForgeSpawnEggItem(IronsSpellbooksCompatEntities.GUARDIAN_WITCH,
                            0x2E2A55,
                            0xF2E2A8,
                            new Item.Properties()));

    // 万法酒狐佩戴的星之魔女系列装备
    public static final RegistryObject<Item> STAR_SHADOW_LONGSWORD =
            ITEMS.register("star_shadow_longsword", StarShadowLongswordItem::new);

    public static final RegistryObject<Item> STAR_SHADOW_STAFF =
            ITEMS.register("star_shadow_staff", StarShadowStaffItem::new);

    public static final RegistryObject<Item> STAR_WITCH_HAT =
            ITEMS.register("star_witch_hat", StarWitchHatItem::new);

    /**
     * 星之魔女酒狐刷怪蛋。贴图是成品色，所以两个颜色都给白 —— 见下。
     *
     * <p>刷怪蛋的染色不是可选项：{@code ItemModelGenerator} 会给 {@code item/generated}
     * 声明过的每一层按<b>层号</b>发一个 tintindex（原版 {@code template_spawn_egg} 的
     * layer0/layer1 就是靠这个分别上底色与斑点），而 {@code ItemColors.createDefault}
     * 又给 {@code SpawnEggItem.eggs()} 里的每一颗都注册了
     * {@code (stack, tintIndex) -> egg.getColor(tintIndex)}。于是 layer0 拿到 tint 0，
     * 也就是 {@code ForgeSpawnEggItem} 的「底色」，整张贴图会被乘上去。
     *
     * <p>照旧填 0x5B3B87 的后果是：贴图平均亮度从 86 掉到 25，整颗发黑。
     * 两个颜色都给 0xFFFFFF 即等于不染（0xFFFFFF 乘任何颜色都是它自己）。
     * <b>哪天要是换回 {@code template_spawn_egg}，这两个值得跟着填回配色</b>，
     * 否则会得到一颗纯白蛋。
     */
    public static final RegistryObject<Item> MAGICAL_WINEFOX_BOSS_SPAWN_EGG =
            ITEMS.register("stellar_witch_spawn_egg",
                    () -> new ForgeSpawnEggItem(IronsSpellbooksCompatEntities.MAGICAL_WINEFOX_BOSS,
                            0xFFFFFF,
                            0xFFFFFF,
                            new Item.Properties()));

    private IronsSpellbooksCompatItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
