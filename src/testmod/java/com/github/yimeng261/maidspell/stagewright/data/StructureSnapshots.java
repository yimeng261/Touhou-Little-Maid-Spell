package com.github.yimeng261.maidspell.stagewright.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构模板内容快照（以当前数据为准，由 NBT 模板生成）。
 * <p>规则与 WorldExtract 相同：特殊方块 = 非原版方块 ID；容器 = 战利品表/刷怪笼/手办模型/非空物品；
 * 实体签名 = 类型 + 展示物品 + 装备（狐狸除外）+ 画作图案；女仆取固定一组字段。模板变动时要重新生成。
 */
public final class StructureSnapshots {
    private StructureSnapshots() {}

    public record Template(List<String> specialBlocks, List<String> containers, List<String> entities, List<Map<String, String>> maids) {}

    public static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();

    static {
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_1", new Template(
            List.of(
                "irons_spellbooks:alchemist_cauldron",
                "irons_spellbooks:book_stack",
                "irons_spellbooks:firefly_jar",
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:pedestal",
                "touhou_little_maid:maid_bed",
                "touhou_little_maid_spell:potted_yue_linglan",
                "touhou_little_maid_spell:suppression_stone",
                "touhou_little_maid_spell:yue_linglan"),
            List.of(
                "irons_spellbooks:pedestal|items=irons_spellbooks:graybeard_staff*1",
                "irons_spellbooks:pedestal|items=irons_spellbooks:greater_oakskin_elixir*1",
                "irons_spellbooks:pedestal|items=irons_spellbooks:nature_rune*1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/elven_realm_1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_plains",
                "minecraft:brewing_stand|items=minecraft:glass_bottle*1,minecraft:glass_bottle*1,minecraft:potion*1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/elven_realm_1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/elven_realm_2",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/elven_realm_2",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1"),
            List.of(
                "minecraft:armor_stand|gear=minecraft:leather_chestplate,minecraft:leather_leggings",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair",
                "touhou_little_maid:maid"),
            List.of(
                Map.ofEntries(Map.entry("model_id", "geckolib:winefox_elf"), Map.entry("favorability", "66"), Map.entry("sitting", "1"), Map.entry("task", "touhou_little_maid:idle"), Map.entry("structureSpawn", "0"), Map.entry("backpack", "touhou_little_maid:empty"), Map.entry("scheduleMode", "DAY"), Map.entry("hand", ","), Map.entry("armor", ",,,"), Map.entry("inventory", "0:irons_spellbooks:druidic_spell_book,1:goety:focus_bag"), Map.entry("baubles", "7:touhou_little_maid_spell:spring_bloom_return"), Map.entry("blueNoteSpells", ""), Map.entry("effects", "irons_spellbooks:vigor,minecraft:regeneration,minecraft:resistance")))));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_10", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_11", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_12", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_13", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_14", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_2", new Template(
            List.of(
                "irons_spellbooks:firefly_jar",
                "irons_spellbooks:pedestal",
                "monsters_and_girls:glow_berry_bush_unlit",
                "touhou_little_maid_spell:yue_linglan"),
            List.of(
                "irons_spellbooks:pedestal|items=irons_spellbooks:lesser_spell_slot_upgrade*1",
                "minecraft:barrel|loot=minecraft:chests/jungle_temple",
                "minecraft:barrel|loot=minecraft:chests/jungle_temple",
                "minecraft:barrel|loot=minecraft:chests/jungle_temple",
                "minecraft:barrel|loot=minecraft:chests/jungle_temple",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/elven_realm_2",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/elven_realm_2",
                "minecraft:chest|items=goety:quick_growing_seed*1,goety:quick_growing_seed*1,goety:quick_growing_seed*1,goety:quick_growing_seed*1,goety:wild_crown*1,irons_spellbooks:arcane_essence*10,irons_spellbooks:arcane_essence*10,irons_spellbooks:arcane_essence*10,irons_spellbooks:arcane_essence*10,irons_spellbooks:arcane_essence*10,irons_spellbooks:plagued_boots*1,irons_spellbooks:plagued_chestplate*1,irons_spellbooks:plagued_leggings*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1,minecraft:glow_berries*1"),
            List.of(
                "touhou_little_maid_spell:elf_templar|gear=irons_spellbooks:claymore"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_3", new Template(
            List.of(
                "irons_spellbooks:firefly_jar",
                "touhou_little_maid_spell:yue_linglan"),
            List.of(
                "minecraft:chest|items=irons_spellbooks:claymore*1,irons_spellbooks:mithril_ingot*1,irons_spellbooks:mithril_scrap*1,irons_spellbooks:mithril_scrap*1,minecraft:lily_of_the_valley*1,minecraft:lily_of_the_valley*1,minecraft:lily_of_the_valley*1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_snowy"),
            List.of(
                "touhou_little_maid_spell:elf_templar|gear=irons_spellbooks:claymore,irons_spellbooks:wizard_boots,irons_spellbooks:wizard_chestplate,irons_spellbooks:wizard_helmet"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_4", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_5", new Template(
            List.of(
                "touhou_little_maid_spell:yue_linglan"),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_6", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_7", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_8", new Template(
            List.of(
                "touhou_little_maid_spell:yue_linglan"),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:elven_realm/elven_realm_9", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/graveyard", new Template(
            List.of(
                "goety:cursed_bars",
                "goety:haunted_chest",
                "goety:haunted_fence",
                "goety:haunted_log",
                "goety:haunted_trapdoor",
                "goety:haunted_wood",
                "goety:pedestal_stone",
                "goety:potted_haunted_sapling",
                "goety:shade_bricks_slab",
                "goety:shade_bricks_wall",
                "goety:shade_stone_bricks",
                "goety:shade_stone_bricks_slab",
                "goety:shade_stone_bricks_stairs",
                "goety:shade_stone_bricks_wall",
                "goety:shade_stone_chiseled",
                "goety:shade_stone_polished",
                "goety:shade_stone_polished_slab",
                "goety:shade_stone_polished_stairs",
                "goety:shade_tiles",
                "goety:shade_tiles_slab",
                "goety:shade_tiles_stairs",
                "goety:soul_light",
                "touhou_little_maid:scarecrow"),
            List.of(
                "goety:haunted_chest|items=goety:blossoming_focus*1,goety:quick_growing_seed*1,goety:quick_growing_seed*1,goety:quick_growing_seed*1,goety:quick_growing_seed*1,goety:quick_growing_seed*1,goety:quick_growing_seed*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1",
                "goety:pedestal_stone|items=goety:soul_emerald*1",
                "minecraft:chest|loot=goety:chests/graveyard_loot",
                "minecraft:chest|loot=goety:chests/graveyard_loot",
                "minecraft:chest|loot=goety:chests/graveyard_treasure",
                "minecraft:chest|loot=goety:chests/graveyard_treasure",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/igloo", new Template(
            List.of(),
            List.of(
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_igloo",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "minecraft:armor_stand|gear=minecraft:leather_helmet",
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/mushroom_fields", new Template(
            List.of(
                "irons_spellbooks:book_stack",
                "irons_spellbooks:firefly_jar",
                "touhou_little_maid:maid_bed"),
            List.of(
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/elven_realm_2",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_mushroom",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:smoker|items=minecraft:charcoal*5"),
            List.of(
                "minecraft:item_frame|item=minecraft:clock",
                "minecraft:item_frame|item=minecraft:mushroom_stew",
                "minecraft:item_frame|item=minecraft:written_book",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/outpost/base_plate", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/outpost/feature_cage1", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/outpost/feature_cage2", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/outpost/feature_cage3", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/outpost/feature_plate", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/outpost/watchtower", new Template(
            List.of(
                "irons_spellbooks:armor_pile",
                "irons_spellbooks:inscription_table",
                "touhou_little_maid:maid_bed"),
            List.of(
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_pillager_outpost_1",
                "minecraft:brewing_stand|items=minecraft:potion*1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_pillager_outpost_2",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "minecraft:allay",
                "minecraft:armor_stand",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/desert_house", new Template(
            List.of(
                "irons_spellbooks:book_stack",
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:mithril_ore",
                "irons_spellbooks:pedestal",
                "touhou_little_maid:maid_bed"),
            List.of(
                "irons_spellbooks:pedestal|items=minecraft:sniffer_egg*1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_desert",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "minecraft:armor_stand|gear=irons_spellbooks:wandering_magician_chestplate",
                "minecraft:item_frame|item=irons_spellbooks:scroll",
                "minecraft:item_frame|item=minecraft:brush",
                "minecraft:item_frame|item=minecraft:iron_pickaxe",
                "minecraft:item_frame|item=minecraft:iron_shovel",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/desert_street", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/plains_house", new Template(
            List.of(
                "irons_spellbooks:alchemist_cauldron",
                "irons_spellbooks:book_stack",
                "irons_spellbooks:firefly_jar",
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:scroll_forge",
                "touhou_little_maid:bookshelf",
                "touhou_little_maid:maid_bed"),
            List.of(
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_plains",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "minecraft:armor_stand|gear=irons_spellbooks:wizard_leggings,minecraft:chainmail_boots",
                "minecraft:glow_item_frame|item=irons_spellbooks:epic_ink",
                "minecraft:item_frame|item=irons_spellbooks:legendary_ink",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/plains_street", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/savanna_house", new Template(
            List.of(
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:pedestal",
                "touhou_little_maid:maid_bed"),
            List.of(
                "irons_spellbooks:pedestal|items=irons_spellbooks:mithril_weave*1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_savanna",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "minecraft:armor_stand|gear=irons_spellbooks:wizard_chestplate",
                "minecraft:armor_stand|gear=minecraft:leather_boots,minecraft:leather_chestplate,minecraft:leather_helmet,minecraft:leather_leggings",
                "touhou_little_maid:chair",
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/savanna_street", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/snowy_house", new Template(
            List.of(
                "irons_spellbooks:alchemist_cauldron",
                "irons_spellbooks:book_stack",
                "irons_spellbooks:inscription_table",
                "touhou_little_maid:maid_bed"),
            List.of(
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_snowy",
                "minecraft:brewing_stand|items=minecraft:potion*1",
                "minecraft:brewing_stand|items=minecraft:potion*1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:furnace|items=irons_spellbooks:cinder_essence*6",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "minecraft:armor_stand|gear=irons_spellbooks:cryomancer_leggings",
                "minecraft:armor_stand|gear=minecraft:leather_boots,minecraft:leather_chestplate,minecraft:leather_helmet,minecraft:leather_leggings",
                "minecraft:fox",
                "minecraft:item_frame|item=minecraft:blaze_powder",
                "minecraft:painting|variant=touhou_little_maid:wine_fox"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/snowy_street", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/taiga_house", new Template(
            List.of(
                "irons_spellbooks:arcane_anvil",
                "irons_spellbooks:book_stack",
                "irons_spellbooks:firefly_jar",
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:pedestal",
                "touhou_little_maid:maid_bed"),
            List.of(
                "irons_spellbooks:pedestal|items=irons_spellbooks:amethyst_rapier*1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_taiga",
                "minecraft:blast_furnace|items=irons_spellbooks:cinder_essence*6",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "minecraft:armor_stand|gear=minecraft:iron_boots,minecraft:iron_chestplate,minecraft:iron_helmet,minecraft:iron_leggings",
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/village/taiga_street", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:enchantress_footsteps/woods_perch", new Template(
            List.of(
                "irons_spellbooks:firefly_jar",
                "touhou_little_maid_spell:potted_yue_linglan"),
            List.of(
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:brewing_stand|items=minecraft:potion*1,minecraft:potion*1,minecraft:potion*1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/elven_realm_2",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1",
                "minecraft:lectern|items=minecraft:written_book*1"),
            List.of(
                "touhou_little_maid:chair"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fairy_maid_cafe/fairy_maid_cafe", new Template(
            List.of(
                "farmersdelight:apple_pie",
                "farmersdelight:beetroot_crate",
                "farmersdelight:cabbage_crate",
                "farmersdelight:canvas_rug",
                "farmersdelight:carrot_crate",
                "farmersdelight:cooking_pot",
                "farmersdelight:cutting_board",
                "farmersdelight:jungle_cabinet",
                "farmersdelight:mangrove_cabinet",
                "farmersdelight:oak_cabinet",
                "farmersdelight:potato_crate",
                "farmersdelight:rice_roll_medley_block",
                "farmersdelight:skillet",
                "farmersdelight:stove",
                "farmersdelight:sweet_berry_cheesecake",
                "farmersdelight:tatami",
                "farmersdelight:wall_hanging_canvas_sign",
                "touhou_little_maid:maid_bed",
                "touhou_little_maid:snack_cabinet",
                "youkaishomecoming:black_tea_bag",
                "youkaishomecoming:coffee_bean_bag",
                "youkaishomecoming:copper_tank",
                "youkaishomecoming:cross_framed_sikkui_stairs",
                "youkaishomecoming:cross_framed_sikkui_trap_door",
                "youkaishomecoming:cross_framed_sikkui_vertical_slab",
                "youkaishomecoming:dark_oak_dining_chair",
                "youkaishomecoming:drying_rack",
                "youkaishomecoming:fine_grid_framed_shoji",
                "youkaishomecoming:framed_sikkui",
                "youkaishomecoming:grid_framed_sikkui",
                "youkaishomecoming:kettle",
                "youkaishomecoming:moka_kit",
                "youkaishomecoming:moka_pot",
                "youkaishomecoming:moon_lantern",
                "youkaishomecoming:oak_dining_chair",
                "youkaishomecoming:oak_dining_table",
                "youkaishomecoming:saucer",
                "youkaishomecoming:sikkui",
                "youkaishomecoming:spruce_dining_chair",
                "youkaishomecoming:spruce_dining_table",
                "youkaishomecoming:steamer_rack",
                "youkaishomecoming:tea_leaf_bag",
                "youkaishomecoming:white_tea_bag",
                "youkaishomecoming:wild_udumbara"),
            List.of(
                "farmersdelight:cooking_pot|items=minecraft:bowl*1",
                "farmersdelight:cutting_board|items=farmersdelight:iron_knife*1",
                "farmersdelight:cutting_board|items=farmersdelight:iron_knife*1",
                "farmersdelight:jungle_cabinet|items=minecraft:leather_boots*1,minecraft:leather_chestplate*1,minecraft:leather_helmet*1,minecraft:leather_leggings*1",
                "farmersdelight:jungle_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_asset",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:mangrove_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:oak_cabinet|items=youkaishomecoming:affogato*1,youkaishomecoming:black_tea*1,youkaishomecoming:con_panna*1,youkaishomecoming:con_panna*1,youkaishomecoming:espresso*1,youkaishomecoming:green_tea*1,youkaishomecoming:green_tea*1,youkaishomecoming:latte*1,youkaishomecoming:macchiato*1,youkaishomecoming:mocha*1,youkaishomecoming:mocha*1,youkaishomecoming:ristretto*1,youkaishomecoming:ristretto*1,youkaishomecoming:sakura_honey_tea*1,youkaishomecoming:tea_mocha*1,youkaishomecoming:tea_mocha*1",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_asset",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_asset",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_dessert",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_dessert",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_dessert",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_dessert",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_dessert",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_drink",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_drink",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_drink",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:oak_cabinet|loot=touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient",
                "farmersdelight:skillet|items=farmersdelight:skillet*1",
                "minecraft:barrel|items=minecraft:diamond*1,minecraft:diamond*1,minecraft:diamond*1,minecraft:diamond*1,minecraft:enchanted_golden_apple*1",
                "minecraft:barrel|items=youkaishomecoming:dassai*1,youkaishomecoming:full_moons_eve*1,youkaishomecoming:hakutsuru*1,youkaishomecoming:kiku*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:suigei*1",
                "minecraft:barrel|items=youkaishomecoming:green_water*1,youkaishomecoming:green_water*1,youkaishomecoming:green_water*1,youkaishomecoming:green_water*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1",
                "minecraft:barrel|items=youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1",
                "minecraft:barrel|items=youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mead*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:mio*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1,youkaishomecoming:sparrow_sake*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:furnace|items=minecraft:coal*10",
                "minecraft:smoker|items=minecraft:coal*10",
                "minecraft:spawner|spawn=touhou_little_maid:fairy",
                "minecraft:spawner|spawn=touhou_little_maid:fairy",
                "youkaishomecoming:steamer_rack|items=farmersdelight:dumplings*1,farmersdelight:dumplings*1,farmersdelight:dumplings*1,farmersdelight:dumplings*1,youkaishomecoming:raw_bun*1,youkaishomecoming:raw_bun*1,youkaishomecoming:raw_bun*1,youkaishomecoming:raw_bun*1"),
            List.of(
                "minecraft:armor_stand|gear=minecraft:iron_boots,minecraft:iron_chestplate,minecraft:iron_helmet,minecraft:iron_leggings",
                "minecraft:glow_item_frame",
                "minecraft:glow_item_frame|item=minecraft:cocoa_beans",
                "minecraft:glow_item_frame|item=youkaishomecoming:coffee_berries",
                "minecraft:painting|variant=minecraft:aztec2",
                "minecraft:painting|variant=minecraft:sunset",
                "touhou_little_maid:fairy|gear=minecraft:leather_helmet",
                "touhou_little_maid:maid|gear=farmersdelight:skillet"),
            List.of(
                Map.ofEntries(Map.entry("model_id", "geckolib:foxmaid"), Map.entry("favorability", "64"), Map.entry("sitting", "1"), Map.entry("task", "touhou_little_maid:idle"), Map.entry("structureSpawn", "0"), Map.entry("backpack", "touhou_little_maid:small_backpack"), Map.entry("scheduleMode", "ALL"), Map.entry("hand", "farmersdelight:skillet,"), Map.entry("armor", ",,,"), Map.entry("inventory", "0:youkaishomecoming:udumbara_flower"), Map.entry("baubles", "7:touhou_little_maid_spell:fragrant_ingenuity"), Map.entry("blueNoteSpells", ""), Map.entry("effects", "youkaishomecoming:refreshing")))));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_1", new Template(
            List.of(
                "goety:dark_altar",
                "goety:pedestal",
                "goety:shade_soul_brazier",
                "goety:soul_light",
                "touhou_little_maid_spell:scarlet_zhuhua",
                "touhou_little_maid_spell:suppression_stone"),
            List.of(),
            List.of(
                "touhou_little_maid:maid|gear=minecraft:elytra,minecraft:netherite_sword"),
            List.of(
                Map.ofEntries(Map.entry("model_id", "geckolib:winefox_saint_c4f14815c46c13e6eb3d82ba88ed2f96"), Map.entry("favorability", "0"), Map.entry("sitting", "1"), Map.entry("task", "maidspell:spell_combat_far"), Map.entry("structureSpawn", "0"), Map.entry("backpack", "touhou_little_maid:empty"), Map.entry("scheduleMode", "ALL"), Map.entry("hand", "minecraft:netherite_sword,"), Map.entry("armor", ",,minecraft:elytra,"), Map.entry("inventory", "0:irons_spellbooks:cursed_doll_spell_book,1:goety:focus_pack"), Map.entry("baubles", "2:touhou_little_maid_spell:bleeding_heart,7:touhou_little_maid_spell:molten_fox_leaf"), Map.entry("blueNoteSpells", ""), Map.entry("effects", "goety:leeching,goety:save_effects,minecraft:regeneration,minecraft:weakness")))));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_10", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_11", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_2", new Template(
            List.of(
                "irons_spellbooks:brazier_soul"),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_3", new Template(
            List.of(
                "goety:haunted_bookshelf",
                "goety:pedestal",
                "goety:pedestal_crypt_stone",
                "goety:shade_soul_brazier",
                "goety:soul_light",
                "irons_spellbooks:armor_pile",
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:scroll_forge",
                "touhou_little_maid_spell:scarlet_zhuhua"),
            List.of(
                "goety:pedestal|items=goety:shadow_essence*1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/fallen_sanctum",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/fallen_sanctum",
                "minecraft:chest|items=goety:forbidden_piece*1,goety:forbidden_piece*1,goety:forbidden_piece*1,goety:forbidden_piece*1,goety:forbidden_piece*1,goety:soul_emerald*1,irons_spellbooks:ancient_knowledge_fragment*2,irons_spellbooks:ancient_knowledge_fragment*2,irons_spellbooks:ancient_knowledge_fragment*2,irons_spellbooks:ancient_knowledge_fragment*2,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:blood_vial*4,irons_spellbooks:bloody_vellum*5,irons_spellbooks:bloody_vellum*5,irons_spellbooks:cooldown_upgrade_orb*1,irons_spellbooks:mana_upgrade_orb*1,minecraft:netherite_upgrade_smithing_template*1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/fallen_sanctum",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:spawner|spawn=goety:reaper",
                "minecraft:spawner|spawn=goety:reaper"),
            List.of(
                "irons_spellbooks:citadel_keeper|gear=irons_spellbooks:cultist_boots,irons_spellbooks:cultist_leggings,irons_spellbooks:legionnaire_flamberge",
                "touhou_little_maid_spell:corrupted_knight|gear=irons_spellbooks:claymore,irons_spellbooks:cultist_boots,irons_spellbooks:cultist_leggings,irons_spellbooks:netherite_mage_helmet,irons_spellbooks:pyromancer_chestplate"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_4", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_5", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_6", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_7", new Template(
            List.of(),
            List.of(
                "minecraft:chest|items=irons_spellbooks:cultist_boots*1,irons_spellbooks:cultist_chestplate*1,irons_spellbooks:cultist_helmet*1,irons_spellbooks:cultist_leggings*1,minecraft:diamond_boots*1,minecraft:diamond_chestplate*1,minecraft:diamond_helmet*1,minecraft:diamond_leggings*1,minecraft:enchanted_golden_apple*1",
                "minecraft:chest|loot=minecraft:chests/ancient_city",
                "minecraft:spawner|spawn=goety:reaper"),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_8", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:fallen_sanctum/fallen_sanctum_9", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_cherry_tree/hidden_cherry_tree", new Template(
            List.of(
                "touhou_little_maid:shrine"),
            List.of(
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_cherry_tree"),
            List.of(
                "minecraft:fox"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_base_left", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_base_right", new Template(
            List.of(
                "irons_spellbooks:firefly_jar",
                "irons_spellbooks:pedestal"),
            List.of(
                "irons_spellbooks:pedestal|items=touhou_little_maid_spell:anchor_core*1",
                "minecraft:barrel|items=minecraft:cherry_sapling*1,minecraft:cherry_sapling*1,minecraft:cherry_sapling*1,minecraft:peony*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:pink_petals*1,minecraft:rose_bush*1,touhou_little_maid_spell:hairpin*1"),
            List.of(
                "minecraft:fox",
                "minecraft:fox"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_building", new Template(
            List.of(
                "farmersdelight:cooking_pot",
                "farmersdelight:skillet",
                "farmersdelight:stove",
                "irons_spellbooks:arcane_anvil",
                "irons_spellbooks:armor_pile",
                "irons_spellbooks:firefly_jar",
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:pedestal",
                "irons_spellbooks:scroll_forge",
                "touhou_little_maid:garage_kit",
                "touhou_little_maid:maid_beacon",
                "touhou_little_maid:maid_bed",
                "touhou_little_maid:scarecrow",
                "touhou_little_maid:shrine",
                "touhou_little_maid:snack_cabinet"),
            List.of(
                "farmersdelight:cooking_pot|items=minecraft:bowl*1",
                "farmersdelight:skillet|items=farmersdelight:skillet*1",
                "irons_spellbooks:pedestal|items=irons_spellbooks:lesser_spell_slot_upgrade*1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_accessories",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_accessories",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_ingredient",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_ingredient",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_ingredient",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_ingredient",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_spell",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_spell",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_spell",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_stuff",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_stuff",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_stuff",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/hidden_hetreat_stuff",
                "minecraft:blast_furnace|items=minecraft:coal*10",
                "minecraft:brewing_stand|items=minecraft:potion*1,minecraft:potion*1,minecraft:potion*1",
                "minecraft:brewing_stand|items=minecraft:potion*1,minecraft:potion*1,minecraft:potion*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:enchanted_book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:enchanted_book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:enchanted_book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1,minecraft:enchanted_book*1",
                "minecraft:furnace|items=minecraft:coal*10",
                "minecraft:lectern|items=minecraft:writable_book*1",
                "minecraft:smoker|items=minecraft:coal*10",
                "touhou_little_maid:garage_kit|model=geckolib:winefox_magical",
                "touhou_little_maid:garage_kit|model=geckolib:winefox_magical_3b7aba412b5ededbab0780e258207bf0",
                "touhou_little_maid:garage_kit|model=geckolib:winefox_magical_a12dc19190c076d8d24aff8714515c8e",
                "touhou_little_maid:garage_kit|model=geckolib:winefox_magical_b073380075f0d30ea8bbcde8566f825e"),
            List.of(
                "minecraft:armor_stand|gear=irons_spellbooks:netherite_mage_chestplate,irons_spellbooks:netherite_mage_helmet",
                "minecraft:bat",
                "minecraft:bat",
                "minecraft:fox",
                "minecraft:fox",
                "minecraft:glow_item_frame|item=irons_spellbooks:amethyst_rapier",
                "minecraft:glow_item_frame|item=irons_spellbooks:artificer_cane",
                "minecraft:glow_item_frame|item=irons_spellbooks:cooldown_upgrade_orb",
                "minecraft:glow_item_frame|item=irons_spellbooks:mana_upgrade_orb",
                "minecraft:glow_item_frame|item=irons_spellbooks:protection_upgrade_orb",
                "minecraft:glow_item_frame|item=touhou_little_maid:broom",
                "minecraft:glow_item_frame|item=touhou_little_maid_spell:chaos_book",
                "minecraft:glow_item_frame|item=touhou_little_maid_spell:soul_book",
                "minecraft:item_frame|item=minecraft:cookie",
                "minecraft:item_frame|item=minecraft:glow_berries",
                "minecraft:painting|variant=minecraft:sunset",
                "minecraft:painting|variant=touhou_little_maid:wine_fox",
                "touhou_little_maid:chair",
                "touhou_little_maid:maid|gear=irons_spellbooks:artificer_cane,irons_spellbooks:netherite_mage_boots,irons_spellbooks:netherite_mage_chestplate,irons_spellbooks:netherite_mage_helmet,irons_spellbooks:netherite_mage_leggings"),
            List.of(
                Map.ofEntries(Map.entry("model_id", "geckolib:winefox_magical_3b7aba412b5ededbab0780e258207bf0"), Map.entry("favorability", "384"), Map.entry("sitting", "1"), Map.entry("task", "touhou_little_maid:idle"), Map.entry("structureSpawn", "0"), Map.entry("backpack", "touhou_little_maid:big_backpack"), Map.entry("scheduleMode", "ALL"), Map.entry("hand", "irons_spellbooks:artificer_cane,"), Map.entry("armor", "irons_spellbooks:netherite_mage_boots,irons_spellbooks:netherite_mage_leggings,irons_spellbooks:netherite_mage_chestplate,irons_spellbooks:netherite_mage_helmet"), Map.entry("inventory", "0:irons_spellbooks:druidic_spell_book,1:irons_spellbooks:amethyst_rapier,2:touhou_little_maid:smart_slab_empty,12:minecraft:dandelion,13:minecraft:poppy,14:minecraft:blue_orchid,15:minecraft:allium,16:minecraft:azure_bluet,17:minecraft:cornflower,18:minecraft:oxeye_daisy,19:minecraft:orange_tulip,20:minecraft:white_tulip,21:minecraft:pink_tulip,22:minecraft:red_tulip,23:minecraft:lily_of_the_valley,24:minecraft:pink_petals,25:minecraft:pink_petals,26:minecraft:pink_petals,27:minecraft:pink_petals,28:minecraft:pink_petals,29:minecraft:pink_petals,30:minecraft:pink_petals,31:minecraft:pink_petals,32:minecraft:pink_petals,33:minecraft:pink_petals,34:minecraft:pink_petals,35:minecraft:pink_petals"), Map.entry("baubles", "7:touhou_little_maid_spell:hairpin,11:touhou_little_maid_spell:quick_chant_ring,12:touhou_little_maid_spell:flow_core,13:touhou_little_maid_spell:blue_note,17:touhou_little_maid_spell:double_heart_chain,22:touhou_little_maid_spell:spell_enhancement_core,27:touhou_little_maid_spell:ender_pocket"), Map.entry("blueNoteSpells", "irons_spellbooks:blessing_of_life,irons_spellbooks:fortify,irons_spellbooks:healing_circle"), Map.entry("effects", "")))));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_flowers", new Template(
            List.of(),
            List.of(),
            List.of(
                "minecraft:fox"),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_leaves_back", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_tree_back", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_tree_front", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_tree_front_top", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:hidden_retreat/hidden_retreat_tree_left", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:relic_sanctum/relic_sanctum_1", new Template(
            List.of(
                "irons_spellbooks:armor_pile",
                "irons_spellbooks:book_stack",
                "irons_spellbooks:inscription_table",
                "irons_spellbooks:pedestal",
                "irons_spellbooks:wisewood_bookshelf",
                "touhou_little_maid_spell:jingxu_youlan"),
            List.of(
                "irons_spellbooks:pedestal|items=irons_spellbooks:gold_spell_book*1",
                "irons_spellbooks:pedestal|items=touhou_little_maid_spell:transmog_necklace*1",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_food",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_taiga",
                "minecraft:barrel|loot=touhou_little_maid_spell:chests/holy_relic_chapel_1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_pillager_outpost_1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_asset",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_savanna",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/enchantress_footsteps_village_snowy",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/hidden_hetreat_stuff",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/holy_relic_chapel_1",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/holy_relic_chapel_2",
                "minecraft:chest|loot=touhou_little_maid_spell:chests/holy_relic_chapel_2",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1",
                "minecraft:chiseled_bookshelf|items=minecraft:book*1,minecraft:book*1,minecraft:enchanted_book*1",
                "minecraft:lectern|items=minecraft:writable_book*1"),
            List.of(
                "irons_spellbooks:citadel_keeper|gear=irons_spellbooks:keeper_flamberge,irons_spellbooks:netherite_mage_boots,irons_spellbooks:netherite_mage_helmet,irons_spellbooks:pumpkin_chestplate",
                "minecraft:glow_item_frame|item=irons_spellbooks:spellbreaker",
                "minecraft:item_frame|item=irons_spellbooks:shriving_stone",
                "touhou_little_maid:maid|gear=irons_spellbooks:magehunter,irons_spellbooks:priest_helmet",
                "touhou_little_maid_spell:holy_construct",
                "touhou_little_maid_spell:shadow_assassin|gear=irons_spellbooks:amethyst_rapier,irons_spellbooks:shadowwalker_boots,irons_spellbooks:shadowwalker_leggings"),
            List.of(
                Map.ofEntries(Map.entry("model_id", "geckolib:winefox_saint"), Map.entry("favorability", "0"), Map.entry("sitting", "1"), Map.entry("task", "maidspell:spell_combat_far"), Map.entry("structureSpawn", "0"), Map.entry("backpack", "touhou_little_maid:empty"), Map.entry("scheduleMode", "ALL"), Map.entry("hand", "irons_spellbooks:magehunter,"), Map.entry("armor", ",,,irons_spellbooks:priest_helmet"), Map.entry("inventory", "0:irons_spellbooks:villager_spell_book"), Map.entry("baubles", "2:touhou_little_maid_spell:blue_note,7:touhou_little_maid_spell:arc_cross"), Map.entry("blueNoteSpells", "irons_spellbooks:blessing_of_life,irons_spellbooks:healing_circle"), Map.entry("effects", "irons_spellbooks:fortify,irons_spellbooks:thunderstorm")))));
        TEMPLATES.put("touhou_little_maid_spell:relic_sanctum/relic_sanctum_2", new Template(
            List.of(),
            List.of(
                "minecraft:barrel|items=touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:power_point*1,touhou_little_maid:smart_slab_empty*1"),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:relic_sanctum/relic_sanctum_3", new Template(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
        TEMPLATES.put("touhou_little_maid_spell:yin_yang_altar/yin_yang_altar", new Template(
            List.of(
                "goety:crypt_urn",
                "goety:cursed_cage",
                "goety:dark_altar",
                "goety:haunted_chest",
                "goety:haunted_fence",
                "goety:haunted_jug",
                "goety:haunted_log",
                "goety:pedestal",
                "goety:rotten_log",
                "goety:rotten_trapdoor",
                "goety:shade_brazier",
                "goety:shade_bricks_wall",
                "goety:shade_soul_brazier",
                "goety:shade_stone_bricks",
                "goety:shade_stone_bricks_stairs",
                "goety:shade_stone_chiseled",
                "goety:shade_stone_polished_slab",
                "goety:shade_tiles",
                "goety:steep_wall"),
            List.of(
                "goety:haunted_chest|loot=touhou_little_maid_spell:chests/goety_maid_altar_chest",
                "minecraft:spawner|spawn=goety:wraith"),
            List.of(),
            List.of()));
    }
}

