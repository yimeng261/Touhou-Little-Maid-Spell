package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Kills;
import com.github.yimeng261.maidspell.stagewright.support.Logs;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.magicterra.stagewright.contract.Terrain;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 缺可选模组时的资源与内容：只装车万女仆（{@link #minimal()}）、不装农夫乐事（{@link #noFarmersDelight()}），
 * 以及全套模组下作为对照的一项（{@link #dedicatedServer()}）。
 * <p>这个类在缺模组的拓扑里加载，只能引用原版、NeoForge、车万女仆与本模组的通用类。
 */
public final class MissingModScenes {
    private static final String RAPIER = "irons_spellbooks:amethyst_rapier";
    private static final String RAPIER_NAME = "item.touhou_little_maid_spell.loot.shadow_assassin_rapier";

    /** 挂在铁魔法条件下的结构；不装铁魔法时结构与结构集都不应注册。 */
    private static final List<String> IRONS_STRUCTURES = List.of("elven_realm", "enchantress_footsteps_igloo",
            "enchantress_footsteps_mushroom_fields", "enchantress_footsteps_outpost", "fallen_sanctum", "relic_sanctum",
            "starwatch_tower", "stellar_endshore", "woods_perch");
    private static final List<String> IRONS_STRUCTURE_SETS = List.of("elven_realm_set", "enchantress_footsteps_igloo",
            "enchantress_footsteps_mushroom_fields", "enchantress_footsteps_outpost", "fallen_sanctum_set",
            "relic_sanctum_set", "starwatch_tower_set", "stellar_endshore_set", "woods_perch");
    private static final List<String> GOETY_STRUCTURES = List.of("enchantress_footsteps_graveyard",
            "enchantress_footsteps_oasis", "yin_yang_altar");
    private static final List<String> GOETY_STRUCTURE_SETS = List.of("enchantress_footsteps_graveyard",
            "enchantress_footsteps_oasis", "yin_yang_altar_set");
    /** 不依赖可选模组、只装车万女仆也应在的结构。 */
    private static final List<String> BASE_STRUCTURES = List.of("hidden_retreat", "hidden_cherry_tree", "starfall_garden",
            "cherry_cabin", "enchantress_footsteps_basalt_forge", "enchantress_footsteps_crimson_cabin",
            "enchantress_footsteps_soulvalley_nook", "enchantress_footsteps_warped_perch");

    /** 按联动模组开关生成的结构的箱子：条目里的联动物品用 optional_mod_item，模组缺席时整张表仍然可用。 */
    private static final List<String> OPTIONAL_ITEM_TABLES = List.of("chests/cherry_cabin_food_1",
            "chests/cherry_cabin_food_2", "chests/cherry_cabin_food_3", "chests/cherry_cabin_asset_without_iss",
            "chests/enchantress_footsteps_nether_1", "chests/enchantress_footsteps_nether_2",
            "chests/enchantress_footsteps_nether_3", "chests/enchantress_footsteps_nether_4");

    private static final List<String> IRONS_ITEMS = List.of("star_shadow_longsword", "star_shadow_staff",
            "star_witch_hat", "star_shadow_spear", "corrupted_knight_spawn_egg", "shadow_assassin_spawn_egg",
            "elf_templar_spawn_egg", "astro_mancer_spawn_egg", "holy_construct_spawn_egg", "stellar_witch_spawn_egg");
    private static final List<String> IRONS_ENTITIES = List.of("corrupted_knight", "shadow_assassin", "elf_templar",
            "astro_mancer", "holy_construct", "stellar_witch", "star_shadow_spear");
    private static final List<String> IRONS_RECIPES = List.of("altar/arc_cross", "altar/dream_cat_crystal_accessories",
            "altar/dream_cat_crystal_crossover", "returning_star", "starglint_dagger");
    private static final List<String> IRONS_LOOT_TABLES = List.of("chests/elven_realm_1",
            "chests/enchantress_footsteps_igloo", "chests/holy_relic_chapel_1", "chests/holy_relic_chapel_2",
            "chests/starwatch_tower_1", "entities/corrupted_knight", "entities/elf_templar", "entities/holy_construct",
            "entities/shadow_assassin", "entities/astro_mancer", "entities/stellar_witch");

    /** 隐世之境三类箱子在不装铁魔法时改开的物品（表里的条目，附魔之书由普通书经随机附魔得到）。 */
    private static final Map<String, Set<String>> RETREAT_FALLBACK = Map.of(
            "chests/hidden_hetreat_accessories", Set.of("touhou_little_maid:ultramarine_orb_elixir",
                    "touhou_little_maid:explosion_protect_bauble", "touhou_little_maid:fire_protect_bauble",
                    "touhou_little_maid:projectile_protect_bauble", "touhou_little_maid:magic_protect_bauble",
                    "touhou_little_maid:fall_protect_bauble", "touhou_little_maid:drown_protect_bauble",
                    "touhou_little_maid:nimble_fabric", "touhou_little_maid:item_magnet_bauble",
                    "touhou_little_maid:substitute_jizo", "minecraft:totem_of_undying"),
            "chests/hidden_hetreat_spell", Set.of("minecraft:paper", "minecraft:book", "minecraft:enchanted_book",
                    "minecraft:lapis_lazuli", "minecraft:experience_bottle"),
            "chests/hidden_hetreat_stuff", Set.of("minecraft:amethyst_shard", "minecraft:ender_pearl",
                    "minecraft:glowstone_dust", "minecraft:blaze_powder", "touhou_little_maid:power_point"));

    private MissingModScenes() {
    }

    public static List<Scene> minimal() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("minimal.noDataLoadErrors", 5, ctx -> noDataLoadErrors(ctx, NS)));
        scenes.add(Checks.scene("minimal.ironsContentNotRegistered", 5, MissingModScenes::ironsContentAbsent));
        scenes.add(Checks.scene("minimal.ironsStructuresNotRegistered", 5, ctx -> structuresAbsent(ctx, IRONS_STRUCTURES, IRONS_STRUCTURE_SETS)));
        scenes.add(Checks.scene("minimal.goetyStructuresNotRegistered", 5, ctx -> structuresAbsent(ctx, GOETY_STRUCTURES, GOETY_STRUCTURE_SETS)));
        scenes.add(Checks.scene("minimal.baseStructuresRegistered", 5, MissingModScenes::baseStructuresPresent));
        scenes.add(Checks.scene("minimal.retreatChestsFallBack", 20, MissingModScenes::retreatChestsFallBack));
        scenes.add(Checks.scene("minimal.optionalItemTablesSkipAbsentItems", 20,
                ctx -> optionalItemTables(ctx, Set.of("irons_spellbooks", "farmersdelight", "goety"))));
        scenes.add(Checks.superflat("minimal.maidSpawnsAndTames", 20, MissingModScenes::maidTames));
        scenes.add(Checks.superflat("minimal.dreamCrystalWithoutCurios", 40, MissingModScenes::dreamCrystalWithoutCurios));
        return scenes;
    }

    public static List<Scene> noFarmersDelight() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("no_farmers_delight.noDataLoadErrors", 5, ctx -> noDataLoadErrors(ctx, NS)));
        scenes.add(Checks.scene("no_farmers_delight.shadowAssassinDrops", 40,
                ctx -> LootScenes.checkKillDrops(ctx, NS + "shadow_assassin", NS + "entities/shadow_assassin"))
                .withTerrain(Terrain.SUPERFLAT));
        scenes.add(Checks.superflat("no_farmers_delight.rapierOnlySharpness", 20,
                ctx -> rapierEnchantments(ctx, Map.of("minecraft:sharpness", 5))));
        scenes.add(Checks.scene("no_farmers_delight.cafeNotRegistered", 5, MissingModScenes::cafeAbsent));
        scenes.add(Checks.scene("no_farmers_delight.optionalItemTablesSkipAbsentItems", 20,
                ctx -> optionalItemTables(ctx, Set.of("farmersdelight"))));
        return scenes;
    }

    /** 全套模组下的对照：装了农夫乐事时细剑还带背刺；模组信息声明的可选依赖与版本下限。 */
    public static List<Scene> dedicatedServer() {
        return List.of(Checks.superflat("loot.shadowAssassinRapierWithFarmersDelight", 20,
                        ctx -> rapierEnchantments(ctx, Map.of("minecraft:sharpness", 5, "farmersdelight:backstabbing", 3))),
                Checks.scene("loot.optionalItemsDropWhenModsPresent", 20, MissingModScenes::optionalItemsDropWhenPresent),
                Checks.scene("loot.optionalModItemSkipsUnknownItem", 20, MissingModScenes::optionalItemSkipsUnknown),
                Checks.scene("loot.optionalModItemKeepsWeightAndQuality", 5, MissingModScenes::optionalItemWeight),
                Checks.scene("mods_toml.optionalDependencies", 5, MissingModScenes::optionalDependencies));
    }

    /** 可选依赖 → 版本范围。低于下限的版本因范围不满足无法启动（由 NeoForge 负责，这里只核对声明）。 */
    private static final Map<String, String> OPTIONAL_DEPENDENCIES = Map.of("goety", "[3.0.0,)",
            "youkaishomecoming", "[3.0.0,)", "youkaisfeasts", "[1.1.1,)", "psi", "[1.21.1-110,2.0.0),(2.0.0,)", "slashblade", "[2.0.0,)", "usefulmagic", "[3.0.0,)",
            "enigmaticaddons", "[0,)");

    private static void optionalDependencies(SceneContext ctx) {
        IModInfo info = ModList.get().getModContainerById(MaidSpellMod.MOD_ID).orElseThrow().getModInfo();
        Map<String, String> declared = new TreeMap<>();
        for (IModInfo.ModVersion dependency : info.getDependencies()) {
            if (OPTIONAL_DEPENDENCIES.containsKey(dependency.getModId())) {
                declared.put(dependency.getModId(), dependency.getType() + " " + dependency.getVersionRange());
            }
        }
        Map<String, String> expected = new TreeMap<>();
        OPTIONAL_DEPENDENCIES.forEach((mod, range) -> expected.put(mod, IModInfo.DependencyType.OPTIONAL + " " + range));
        ctx.check(declared).as("模组信息里的可选依赖").isEqualTo(expected);
    }

    /** 本次启动的日志里没有本模组数据包条目（战利品表、标签、配方、结构）的解析报错。 */
    private static void noDataLoadErrors(SceneContext ctx, String namespace) {
        List<String> problems = Logs.problems(namespace, Logs.DATA_LOAD_ERRORS);
        if (problems == null) {
            ctx.fail("读不到 " + Logs.path());
            return;
        }
        ctx.check(problems).as(Logs.path() + " 里本模组数据的解析报错").isEmpty();
    }

    private static void ironsContentAbsent(SceneContext ctx) {
        List<String> present = new ArrayList<>();
        IRONS_ITEMS.forEach(id -> {
            if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(NS + id))) {
                present.add("物品 " + id);
            }
        });
        IRONS_ENTITIES.forEach(id -> {
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(NS + id))) {
                present.add("实体 " + id);
            }
        });
        IRONS_RECIPES.forEach(id -> {
            if (ctx.server().getRecipeManager().byKey(ResourceLocation.parse(NS + id)).isPresent()) {
                present.add("配方 " + id);
            }
        });
        IRONS_LOOT_TABLES.forEach(id -> {
            if (ctx.loot().exists(NS + id)) {
                present.add("战利品表 " + id);
            }
        });
        ctx.check(present).as("不装铁魔法时仍然存在的铁魔法内容").isEmpty();
    }

    private static void structuresAbsent(SceneContext ctx, List<String> structures, List<String> sets) {
        List<String> present = new ArrayList<>();
        structures.stream().filter(id -> has(ctx, Registries.STRUCTURE, id)).forEach(id -> present.add("结构 " + id));
        sets.stream().filter(id -> has(ctx, Registries.STRUCTURE_SET, id)).forEach(id -> present.add("结构集 " + id));
        ctx.check(present).as("缺少依赖模组时仍然注册的结构").isEmpty();
    }

    private static void baseStructuresPresent(SceneContext ctx) {
        BASE_STRUCTURES.forEach(id -> ctx.check(has(ctx, Registries.STRUCTURE, id)).as("结构 " + id + " 已注册").isTrue());
    }

    private static <T> boolean has(SceneContext ctx, ResourceKey<Registry<T>> registry, String id) {
        return ctx.server().registryAccess().registryOrThrow(registry).containsKey(ResourceLocation.parse(NS + id));
    }

    /** 隐世之境的饰品、墨水、法术材料三类箱子不再是空的，开出的都是替代物品，不含铁魔法物品。 */
    private static void retreatChestsFallBack(SceneContext ctx) {
        RETREAT_FALLBACK.forEach((table, allowed) -> {
            String id = NS + table;
            if (!ctx.loot().exists(id)) {
                ctx.fail("战利品表 " + id + " 未加载");
                return;
            }
            Set<String> rolled = rollChest(ctx, id, 200);
            Set<String> foreign = new TreeSet<>(rolled);
            foreign.removeAll(allowed);
            ctx.record(table, rolled);
            ctx.check(rolled).as(table + " 开 200 次的物品").isNotEmpty();
            ctx.check(foreign).as(table + " 里不在替代清单内的物品").isEmpty();
        });
    }

    /** 装着联动模组时，条目里的联动物品会正常掉落。 */
    private static void optionalItemsDropWhenPresent(SceneContext ctx) {
        Set<String> food = rollChest(ctx, NS + "chests/cherry_cabin_food_1", 200);
        ctx.record("cherry_cabin_food_1", food);
        ctx.check(food.stream().anyMatch(id -> id.startsWith("farmersdelight:"))).as("cherry_cabin_food_1 掉出农夫乐事物品").isTrue();
        Set<String> nether = rollChest(ctx, NS + "chests/enchantress_footsteps_nether_2", 200);
        ctx.record("enchantress_footsteps_nether_2", nether);
        ctx.check(nether.stream().anyMatch(id -> id.startsWith("irons_spellbooks:"))).as("enchantress_footsteps_nether_2 掉出铁魔法物品").isTrue();
    }

    /** 条目指向不存在的物品时不进入抽取，也不让战利品表加载失败；同表里存在的条目照常掉落。 */
    private static void optionalItemSkipsUnknown(SceneContext ctx) {
        JsonObject json = JsonParser.parseString("""
                {"pools":[{"rolls":1,"entries":[
                  {"type":"touhou_little_maid_spell:optional_mod_item","name":"missing_integration:food","weight":100},
                  {"type":"touhou_little_maid_spell:optional_mod_item","name":"minecraft:diamond","weight":1}]}]}
                """).getAsJsonObject();
        var table = LootTable.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE,
                        ctx.server().registryAccess()), json)
                .getOrThrow(message -> new IllegalStateException("无法解析战利品表: " + message));
        var params = new LootParams.Builder(ctx.level())
                .create(LootContextParamSets.EMPTY);
        Set<String> dropped = new TreeSet<>();
        for (int roll = 0; roll < 50; roll++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                dropped.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount());
            }
        }
        ctx.check(dropped).as("缺失物品的条目被跳过，只掉落存在的物品").isEqualTo(Set.of("minecraft:diamond*1"));
    }

    /** 物品存在时条目照常展开：权重按 weight + quality × luck 计算，掉落一个该物品；物品不存在时不展开也不掉落。 */
    private static void optionalItemWeight(SceneContext ctx) {
        var context = new LootContext.Builder(new LootParams.Builder(ctx.level()).withLuck(1.0F)
                .create(LootContextParamSets.EMPTY)).create(Optional.empty());
        List<LootPoolEntry> rolls = new ArrayList<>();
        ctx.check(optionalEntry(ctx, "minecraft:diamond").expand(context, rolls::add)).as("钻石条目展开").isTrue();
        ctx.check(rolls).as("钻石条目展开出的抽取项").hasSize(1);
        if (rolls.size() == 1) {
            ctx.check(rolls.get(0).getWeight(1.0F)).as("weight 3 + quality 2 × luck 1").isEqualTo(5);
            List<String> drops = new ArrayList<>();
            rolls.get(0).createItemStack(stack -> drops.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount()), context);
            ctx.check(drops).as("钻石条目的掉落").isEqualTo(List.of("minecraft:diamond*1"));
        }
        List<LootPoolEntry> missing = new ArrayList<>();
        ctx.check(optionalEntry(ctx, "missing_integration:food").expand(context, missing::add)).as("缺失物品条目展开").isFalse();
        ctx.check(missing).as("缺失物品条目展开出的抽取项").isEmpty();
    }

    private static LootPoolEntryContainer optionalEntry(SceneContext ctx, String item) {
        JsonObject json = JsonParser.parseString("""
                {"type":"touhou_little_maid_spell:optional_mod_item","name":"%s","weight":3,"quality":2}
                """.formatted(item)).getAsJsonObject();
        return LootPoolEntries.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, ctx.server().registryAccess()), json)
                .getOrThrow(message -> new IllegalStateException("无法解析战利品条目: " + message));
    }

    /** 缺联动模组时战利品表仍然加载并能抽出物品，且抽不出缺席模组的物品。 */
    private static void optionalItemTables(SceneContext ctx, Set<String> absentNamespaces) {
        for (String table : OPTIONAL_ITEM_TABLES) {
            String id = NS + table;
            if (!ctx.loot().exists(id)) {
                ctx.fail("战利品表 " + id + " 未加载");
                continue;
            }
            Set<String> rolled = rollChest(ctx, id, 200);
            ctx.record(table, rolled);
            ctx.check(rolled).as(table + " 开 200 次的物品").isNotEmpty();
            ctx.check(rolled.stream().filter(item -> absentNamespaces.contains(item.substring(0, item.indexOf(':')))).toList())
                    .as(table + " 里缺席模组的物品").isEmpty();
        }
    }

    private static Set<String> rollChest(SceneContext ctx, String id, int rolls) {
        var pos = ctx.rel(0, 0, 0);
        var previous = ctx.level().getBlockState(pos);
        ctx.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        ctx.cleanup(() -> ctx.level().setBlockAndUpdate(pos, previous));
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) ctx.level().getBlockEntity(pos);
        var table = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id));
        Set<String> items = new TreeSet<>();
        for (int roll = 1; roll <= rolls; roll++) {
            chest.clearContent();
            chest.setLootTable(table, roll);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!stack.isEmpty()) items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return items;
    }

    private static void maidTames(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        ctx.check(maid.isAlive()).as("女仆已生成").isTrue();
        ctx.check(Players.tame(Owners.visitor(ctx, "TlmsMinimalOwner", 2, 0, 0), maid)).as("驯服女仆").isTrue();
    }

    /** 没有 Curios 时梦云水晶仍是女仆饰品：戴上后第一次致死被救下。 */
    private static void dreamCrystalWithoutCurios(SceneContext ctx) {
        DreamCrystalScenes.reviveFirstDeath(ctx);
    }

    /** 击杀一只影刺客，掉落的细剑恰好带这些附魔。 */
    private static void rapierEnchantments(SceneContext ctx, Map<String, Integer> expected) {
        Kills.Result result = Kills.drops(ctx, NS + "shadow_assassin", 1);
        if (result == null) {
            return;
        }
        List<ItemStack> rapiers = result.drops().stream()
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(RAPIER))
                .filter(stack -> stack.get(DataComponents.CUSTOM_NAME) != null
                        && stack.get(DataComponents.CUSTOM_NAME).getContents() instanceof TranslatableContents name
                        && name.getKey().equals(RAPIER_NAME))
                .toList();
        ctx.check(rapiers.size()).as("掉落表给的细剑（" + RAPIER_NAME + "）数").isEqualTo(1);
        if (rapiers.size() != 1) {
            return;
        }
        Map<String, Integer> actual = new TreeMap<>();
        ItemEnchantments enchantments = rapiers.getFirst().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            actual.put(enchantment.getRegisteredName(), enchantments.getLevel(enchantment));
        }
        ctx.check(actual).as("细剑的附魔").isEqualTo(new TreeMap<>(expected));
    }

    /** 妖怪的归家强依赖农夫乐事，这个组合里不装；仙女女仆咖啡厅与它的箱子都不加载。 */
    private static void cafeAbsent(SceneContext ctx) {
        ctx.check(has(ctx, Registries.STRUCTURE, "fairy_maid_cafe")).as("仙女女仆咖啡厅已注册").isFalse();
        for (String table : List.of("asset", "dessert", "drink", "ingredient")) {
            ctx.check(ctx.loot().exists(NS + "chests/fairy_maid_cafe_" + table)).as("咖啡厅 " + table + " 箱子已加载").isFalse();
        }
    }
}
