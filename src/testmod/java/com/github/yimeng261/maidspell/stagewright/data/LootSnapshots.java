package com.github.yimeng261.maidspell.stagewright.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 战利品表可达集合快照（以当前数据为准，由数据文件生成）。
 * <p>键是物品 ID，卷轴/药水附带 [spell=…]/[potion=…] 限定；表数据变动时这里要同步更新。
 */
public final class LootSnapshots {
    private LootSnapshots() {}

    /** 分组名 → 该组包含的战利品表 ID，分组对应一个场景。 */
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    /** 战利品表 ID → 可达集合。 */
    public static final Map<String, Set<String>> REACHABLE = new LinkedHashMap<>();

    static {
        GROUPS.put("hidden_cherry_tree", List.of("touhou_little_maid_spell:chests/hidden_cherry_tree"));
        GROUPS.put("hidden_retreat", List.of("touhou_little_maid_spell:chests/hidden_hetreat_ingredient", "touhou_little_maid_spell:chests/hidden_hetreat_asset", "touhou_little_maid_spell:chests/hidden_hetreat_stuff", "touhou_little_maid_spell:chests/hidden_hetreat_spell", "touhou_little_maid_spell:chests/hidden_hetreat_accessories"));
        GROUPS.put("fairy_maid_cafe", List.of("touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient", "touhou_little_maid_spell:chests/fairy_maid_cafe_dessert", "touhou_little_maid_spell:chests/fairy_maid_cafe_drink", "touhou_little_maid_spell:chests/fairy_maid_cafe_asset"));
        GROUPS.put("yin_yang_altar", List.of("touhou_little_maid_spell:chests/goety_maid_altar_chest"));
        GROUPS.put("relic_sanctum", List.of("touhou_little_maid_spell:chests/holy_relic_chapel_1", "touhou_little_maid_spell:chests/holy_relic_chapel_2"));
        GROUPS.put("fallen_sanctum", List.of("touhou_little_maid_spell:chests/fallen_sanctum", "minecraft:chests/ancient_city"));
        GROUPS.put("elven_realm", List.of("touhou_little_maid_spell:chests/elven_realm_1", "touhou_little_maid_spell:chests/elven_realm_2", "minecraft:chests/jungle_temple"));
        GROUPS.put("enchantress_footsteps_igloo", List.of("touhou_little_maid_spell:chests/enchantress_footsteps_igloo"));
        GROUPS.put("enchantress_footsteps_mushroom_fields", List.of("touhou_little_maid_spell:chests/enchantress_footsteps_mushroom"));
        GROUPS.put("enchantress_footsteps_outpost", List.of("touhou_little_maid_spell:chests/enchantress_footsteps_pillager_outpost_1", "touhou_little_maid_spell:chests/enchantress_footsteps_pillager_outpost_2"));
        GROUPS.put("enchantress_footsteps_village", List.of("touhou_little_maid_spell:chests/enchantress_footsteps_village_food", "touhou_little_maid_spell:chests/enchantress_footsteps_village_asset", "touhou_little_maid_spell:chests/enchantress_footsteps_village_desert", "touhou_little_maid_spell:chests/enchantress_footsteps_village_plains", "touhou_little_maid_spell:chests/enchantress_footsteps_village_savanna", "touhou_little_maid_spell:chests/enchantress_footsteps_village_snowy", "touhou_little_maid_spell:chests/enchantress_footsteps_village_taiga"));
        GROUPS.put("enchantress_footsteps_graveyard", List.of("goety:chests/graveyard_loot", "goety:chests/graveyard_treasure"));
        GROUPS.put("entities", List.of("touhou_little_maid_spell:entities/shadow_assassin", "touhou_little_maid_spell:entities/holy_construct", "touhou_little_maid_spell:entities/corrupted_knight", "touhou_little_maid_spell:entities/elf_templar"));
        REACHABLE.put("touhou_little_maid_spell:chests/hidden_cherry_tree", Set.of(
                "minecraft:glow_berries",
                "minecraft:pink_petals",
                "touhou_little_maid:drown_protect_bauble",
                "touhou_little_maid:explosion_protect_bauble",
                "touhou_little_maid:fall_protect_bauble",
                "touhou_little_maid:fire_protect_bauble",
                "touhou_little_maid:magic_protect_bauble",
                "touhou_little_maid:nimble_fabric",
                "touhou_little_maid:power_point",
                "touhou_little_maid:projectile_protect_bauble",
                "touhou_little_maid_spell:wind_seeking_bell"));
        REACHABLE.put("touhou_little_maid_spell:chests/hidden_hetreat_ingredient", Set.of(
                "minecraft:beef",
                "minecraft:beetroot",
                "minecraft:carrot",
                "minecraft:chicken",
                "minecraft:cod",
                "minecraft:egg",
                "minecraft:enchanted_golden_apple",
                "minecraft:golden_apple",
                "minecraft:golden_carrot",
                "minecraft:honey_bottle",
                "minecraft:melon",
                "minecraft:mutton",
                "minecraft:porkchop",
                "minecraft:potato",
                "minecraft:rabbit",
                "minecraft:salmon",
                "minecraft:sweet_berries"));
        REACHABLE.put("touhou_little_maid_spell:chests/hidden_hetreat_asset", Set.of(
                "minecraft:diamond",
                "minecraft:emerald",
                "minecraft:gold_ingot",
                "minecraft:gold_nugget",
                "touhou_little_maid:power_point"));
        REACHABLE.put("touhou_little_maid_spell:chests/hidden_hetreat_stuff", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:arcane_rune",
                "irons_spellbooks:cooldown_rune",
                "irons_spellbooks:divine_pearl",
                "irons_spellbooks:eldritch_manuscript",
                "irons_spellbooks:holy_rune",
                "irons_spellbooks:magic_cloth",
                "irons_spellbooks:nature_rune",
                "irons_spellbooks:protection_rune",
                "irons_spellbooks:ruined_book"));
        REACHABLE.put("touhou_little_maid_spell:chests/hidden_hetreat_spell", Set.of(
                "irons_spellbooks:epic_ink",
                "irons_spellbooks:legendary_ink",
                "irons_spellbooks:rare_ink",
                "irons_spellbooks:uncommon_ink",
                "minecraft:paper"));
        REACHABLE.put("touhou_little_maid_spell:chests/hidden_hetreat_accessories", Set.of(
                "irons_spellbooks:amethyst_resonance_charm",
                "irons_spellbooks:cast_time_ring",
                "irons_spellbooks:conjurers_talisman",
                "irons_spellbooks:cooldown_ring",
                "irons_spellbooks:emerald_stoneplate_ring",
                "irons_spellbooks:heavy_chain_necklace",
                "irons_spellbooks:mana_ring",
                "irons_spellbooks:poisonward_ring",
                "irons_spellbooks:silver_ring",
                "touhou_little_maid:drown_protect_bauble",
                "touhou_little_maid:explosion_protect_bauble",
                "touhou_little_maid:fall_protect_bauble",
                "touhou_little_maid:fire_protect_bauble",
                "touhou_little_maid:magic_protect_bauble",
                "touhou_little_maid:projectile_protect_bauble",
                "touhou_little_maid:ultramarine_orb_elixir"));
        REACHABLE.put("touhou_little_maid_spell:chests/fairy_maid_cafe_ingredient", Set.of(
                "farmersdelight:cabbage",
                "farmersdelight:tomato",
                "minecraft:beef",
                "minecraft:chicken",
                "minecraft:cod",
                "minecraft:golden_carrot",
                "minecraft:mutton",
                "minecraft:porkchop",
                "minecraft:rabbit",
                "minecraft:salmon",
                "minecraft:suspicious_stew",
                "minecraft:sweet_berries",
                "youkaishomecoming:imitation_bear_paw",
                "youkaishomecoming:raw_lamprey",
                "youkaishomecoming:redbean"));
        REACHABLE.put("touhou_little_maid_spell:chests/fairy_maid_cafe_dessert", Set.of(
                "minecraft:cookie",
                "minecraft:pumpkin_pie",
                "youkaishomecoming:assorted_dango",
                "youkaishomecoming:candy_apple",
                "youkaishomecoming:matcha_mochi",
                "youkaishomecoming:mitarashi_dango",
                "youkaishomecoming:mochi",
                "youkaishomecoming:oyaki",
                "youkaishomecoming:sakura_mochi"));
        REACHABLE.put("touhou_little_maid_spell:chests/fairy_maid_cafe_drink", Set.of(
                "youkaishomecoming:affogato",
                "youkaishomecoming:americano",
                "youkaishomecoming:black_tea",
                "youkaishomecoming:cappuccino",
                "youkaishomecoming:con_panna",
                "youkaishomecoming:espresso",
                "youkaishomecoming:green_tea",
                "youkaishomecoming:latte",
                "youkaishomecoming:macchiato",
                "youkaishomecoming:mocha",
                "youkaishomecoming:ristretto",
                "youkaishomecoming:sakura_honey_tea",
                "youkaishomecoming:tea_mocha"));
        REACHABLE.put("touhou_little_maid_spell:chests/fairy_maid_cafe_asset", Set.of(
                "minecraft:diamond",
                "minecraft:emerald",
                "minecraft:gold_ingot",
                "minecraft:gold_nugget",
                "touhou_little_maid:power_point",
                "touhou_little_maid_spell:wind_seeking_bell"));
        REACHABLE.put("touhou_little_maid_spell:chests/goety_maid_altar_chest", Set.of(
                "goety:ectoplasm",
                "goety:forbidden_fragment",
                "goety:forbidden_piece",
                "goety:infernal_tome",
                "goety:ring_of_force",
                "goety:ring_of_thirst",
                "goety:ring_of_want",
                "goety:warlock_sash",
                "goety:witch_hat_hedge",
                "touhou_little_maid:power_point"));
        REACHABLE.put("touhou_little_maid_spell:chests/holy_relic_chapel_1", Set.of(
                "irons_spellbooks:ancient_knowledge_fragment",
                "irons_spellbooks:common_ink",
                "irons_spellbooks:rare_ink",
                "irons_spellbooks:scroll[spell=irons_spellbooks:angel_wing]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:fortify]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:guiding_bolt]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:heal]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:sunbeam]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:wisp]",
                "irons_spellbooks:uncommon_ink",
                "minecraft:paper"));
        REACHABLE.put("touhou_little_maid_spell:chests/holy_relic_chapel_2", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:concentration_amulet",
                "irons_spellbooks:emerald_stoneplate_ring",
                "irons_spellbooks:mana_ring",
                "irons_spellbooks:silver_ring",
                "minecraft:emerald",
                "touhou_little_maid:projectile_protect_bauble"));
        REACHABLE.put("touhou_little_maid_spell:chests/fallen_sanctum", Set.of(
                "minecraft:air",
                "minecraft:diamond",
                "minecraft:gold_ingot",
                "minecraft:netherite_scrap",
                "minecraft:paper",
                "touhou_little_maid:power_point"));
        REACHABLE.put("minecraft:chests/ancient_city", Set.of(
                "goety:forbidden_fragment",
                "goety:forbidden_piece",
                "goety:warred_scroll",
                "minecraft:amethyst_shard",
                "minecraft:bone",
                "minecraft:book",
                "minecraft:candle",
                "minecraft:coal",
                "minecraft:compass",
                "minecraft:diamond_hoe",
                "minecraft:diamond_horse_armor",
                "minecraft:diamond_leggings",
                "minecraft:disc_fragment_5",
                "minecraft:echo_shard",
                "minecraft:enchanted_golden_apple",
                "minecraft:experience_bottle",
                "minecraft:glow_berries",
                "minecraft:iron_leggings",
                "minecraft:lead",
                "minecraft:music_disc_13",
                "minecraft:music_disc_cat",
                "minecraft:music_disc_otherside",
                "minecraft:name_tag",
                "minecraft:potion[potion=minecraft:strong_regeneration]",
                "minecraft:saddle",
                "minecraft:sculk",
                "minecraft:sculk_catalyst",
                "minecraft:sculk_sensor",
                "minecraft:silence_armor_trim_smithing_template",
                "minecraft:soul_torch",
                "minecraft:ward_armor_trim_smithing_template"));
        REACHABLE.put("touhou_little_maid_spell:chests/elven_realm_1", Set.of(
                "irons_spellbooks:nature_rune",
                "irons_spellbooks:nature_upgrade_orb",
                "irons_spellbooks:poisonward_ring",
                "irons_spellbooks:scroll[spell=irons_spellbooks:blight]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:earthquake]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:gluttony]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:poison_arrow]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:root]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:stomp]",
                "irons_spellbooks:scroll[spell=irons_spellbooks:touch_dig]",
                "minecraft:glow_berries",
                "minecraft:poisonous_potato"));
        REACHABLE.put("touhou_little_maid_spell:chests/elven_realm_2", Set.of(
                "minecraft:azure_bluet",
                "minecraft:honey_bottle",
                "minecraft:honeycomb",
                "minecraft:lily_of_the_valley",
                "minecraft:oxeye_daisy",
                "minecraft:white_tulip"));
        REACHABLE.put("minecraft:chests/jungle_temple", Set.of(
                "goety:floral_scroll",
                "minecraft:bamboo",
                "minecraft:bone",
                "minecraft:book",
                "minecraft:diamond",
                "minecraft:diamond_horse_armor",
                "minecraft:emerald",
                "minecraft:gold_ingot",
                "minecraft:golden_horse_armor",
                "minecraft:iron_horse_armor",
                "minecraft:iron_ingot",
                "minecraft:rotten_flesh",
                "minecraft:saddle",
                "minecraft:wild_armor_trim_smithing_template"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_igloo", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:frozen_bone",
                "minecraft:glow_berries",
                "minecraft:snowball",
                "minecraft:sweet_berries",
                "touhou_little_maid:power_point"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_mushroom", Set.of(
                "minecraft:bowl",
                "minecraft:brown_mushroom",
                "minecraft:mushroom_stew",
                "minecraft:red_mushroom",
                "minecraft:suspicious_stew"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_pillager_outpost_1", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:conjurers_talisman",
                "irons_spellbooks:emerald_stoneplate_ring",
                "irons_spellbooks:silver_ring",
                "minecraft:emerald",
                "touhou_little_maid:projectile_protect_bauble"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_pillager_outpost_2", Set.of(
                "minecraft:arrow",
                "minecraft:crossbow",
                "minecraft:experience_bottle",
                "minecraft:goat_horn",
                "minecraft:potion[potion=minecraft:healing]",
                "minecraft:wheat",
                "touhou_little_maid:maid_backpack_middle",
                "touhou_little_maid:maid_backpack_small",
                "touhou_little_maid:power_point"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_village_food", Set.of(
                "minecraft:apple",
                "minecraft:cookie",
                "minecraft:glow_berries",
                "minecraft:golden_apple",
                "minecraft:golden_carrot",
                "minecraft:pumpkin_pie",
                "minecraft:sweet_berries"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_village_asset", Set.of(
                "irons_spellbooks:divine_pearl",
                "minecraft:diamond",
                "minecraft:emerald",
                "minecraft:gold_ingot",
                "touhou_little_maid:power_point"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_village_desert", Set.of(
                "irons_spellbooks:ancient_knowledge_fragment",
                "irons_spellbooks:raw_mithril",
                "minecraft:diamond",
                "minecraft:gold_nugget",
                "minecraft:lapis_lazuli",
                "minecraft:raw_gold",
                "minecraft:redstone"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_village_plains", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:common_ink",
                "irons_spellbooks:epic_ink",
                "irons_spellbooks:rare_ink",
                "irons_spellbooks:uncommon_ink"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_village_savanna", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:magic_cloth",
                "irons_spellbooks:mithril_weave",
                "minecraft:string"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_village_snowy", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:evasion_elixir",
                "irons_spellbooks:invisibility_elixir",
                "irons_spellbooks:oakskin_elixir",
                "minecraft:potion[potion=irons_spellbooks:instant_mana_one]",
                "minecraft:potion[potion=minecraft:healing]"));
        REACHABLE.put("touhou_little_maid_spell:chests/enchantress_footsteps_village_taiga", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:raw_mithril",
                "irons_spellbooks:shriving_stone",
                "irons_spellbooks:weapon_parts",
                "minecraft:amethyst_shard",
                "minecraft:iron_ingot"));
        REACHABLE.put("goety:chests/graveyard_loot", Set.of(
                "goety:grave_dust",
                "minecraft:amethyst_shard",
                "minecraft:bone",
                "minecraft:book",
                "minecraft:diamond",
                "minecraft:dirt",
                "minecraft:emerald",
                "minecraft:enchanted_golden_apple",
                "minecraft:gold_ingot",
                "minecraft:golden_apple",
                "minecraft:iron_ingot",
                "minecraft:rotten_flesh",
                "minecraft:saddle",
                "minecraft:string"));
        REACHABLE.put("goety:chests/graveyard_treasure", Set.of(
                "goety:ectoplasm",
                "goety:forbidden_piece",
                "goety:grave_dust",
                "goety:plushie",
                "goety:plushie_1",
                "goety:plushie_10",
                "goety:plushie_2",
                "goety:plushie_3",
                "goety:plushie_4",
                "goety:plushie_5",
                "goety:plushie_6",
                "goety:plushie_7",
                "goety:plushie_8",
                "goety:plushie_9",
                "minecraft:amethyst_shard",
                "minecraft:bone",
                "minecraft:book",
                "minecraft:candle",
                "minecraft:diamond",
                "minecraft:enchanted_golden_apple",
                "minecraft:ender_pearl",
                "minecraft:experience_bottle",
                "minecraft:iron_pickaxe",
                "minecraft:iron_shovel",
                "minecraft:iron_sword",
                "minecraft:lead",
                "minecraft:name_tag",
                "minecraft:rotten_flesh",
                "minecraft:soul_torch"));
        REACHABLE.put("touhou_little_maid_spell:entities/shadow_assassin", Set.of(
                "irons_spellbooks:amethyst_rapier[spell=irons_spellbooks:shadow_slash]",
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:ender_rune",
                "irons_spellbooks:greater_evasion_elixir",
                "irons_spellbooks:shadowwalker_boots",
                "irons_spellbooks:weapon_parts"));
        REACHABLE.put("touhou_little_maid_spell:entities/holy_construct", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:energized_core",
                "irons_spellbooks:holy_rune",
                "irons_spellbooks:holy_upgrade_orb",
                "irons_spellbooks:lightning_rune",
                "irons_spellbooks:mithril_scrap",
                "minecraft:air",
                "minecraft:iron_ingot"));
        REACHABLE.put("touhou_little_maid_spell:entities/corrupted_knight", Set.of(
                "irons_spellbooks:claymore[spell=irons_spellbooks:flaming_strike]",
                "irons_spellbooks:cultist_boots",
                "irons_spellbooks:cultist_leggings",
                "irons_spellbooks:netherite_mage_helmet",
                "irons_spellbooks:pyromancer_chestplate",
                "minecraft:emerald",
                "minecraft:netherite_scrap",
                "minecraft:written_book"));
        REACHABLE.put("touhou_little_maid_spell:entities/elf_templar", Set.of(
                "irons_spellbooks:arcane_essence",
                "irons_spellbooks:claymore[spell=irons_spellbooks:gust]",
                "irons_spellbooks:nature_rune",
                "minecraft:air",
                "minecraft:glow_berries",
                "touhou_little_maid_spell:yue_linglan"));
    }
}

