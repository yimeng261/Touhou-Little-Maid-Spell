package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Batch;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.github.yimeng261.maidspell.worldgen.TerrainFeatureGuard;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import com.github.yimeng261.maidspell.worldgen.structure.MultiExclusionSpreadPlacement;
import com.github.yimeng261.maidspell.worldgen.structure.OptionalModTemplateStructure;
import com.github.yimeng261.maidspell.worldgen.structure.RelicSanctumStructure;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 自然生成场景：注册与群系/维度映射快照、固定种子（5471）的真实维度按放置网格抽样、
 * 隐世之境只在归隐之地生成、归隐之地结构白名单与自带地形、自定义地形检查的准确性、村庄补充的自然出现、可选模组模板结构与咖啡厅不悬空、咖啡厅不陷进地里。
 * <p>不借用场地，直接查询服务器的主世界/下界/末地/归隐之地；除悬空检查要完整生成区块外，计算只到 STRUCTURE_STARTS 或只用噪声。
 */
public final class NaturalScenes {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private static final String RETREAT = NS + "the_retreat";

    /** 下界魔女足迹避开要塞与堡垒：要塞的拼图块离起点最远 7 个区块，再加上结构自身的占地。 */
    private static final String NETHER_COMPLEXES = "minecraft:nether_complexes@8";
    /** 绯红林屋还要避开同在绯红森林的堕天圣堂：圣堂占地离起点最远约 6 个区块，再加上林屋自身的占地。 */
    private static final String FALLEN_SANCTUM = NS + "fallen_sanctum_set@7";

    /** 结构注册快照（来自当前数据）：类型、群系条目（# 开头为标签）、地形适配、所属结构集与随机分散放置参数。 */
    private record Reg(String path, String type, List<String> biomes, String adaptation, String set,
                       int spacing, int separation, int salt, float frequency, String exclusion) {
    }

    private static final List<Reg> REGISTRATION = List.of(
            new Reg("cherry_cabin", NS + "optional_mod_template", List.of("minecraft:cherry_grove"), "beard_thin",
                    "cherry_cabin", 40, 16, 1735687961, 1.0F, ""),
            new Reg("elven_realm", NS + "relic_sanctum", List.of("minecraft:flower_forest"), "beard_box",
                    "elven_realm_set", 32, 16, 2063145721, 1.0F, ""),
            new Reg("enchantress_footsteps_graveyard", "minecraft:jigsaw", List.of("minecraft:plains", "minecraft:forest"),
                    "beard_thin", "enchantress_footsteps_graveyard", 36, 16, 1248316573, 1.0F, ""),
            new Reg("enchantress_footsteps_igloo", "minecraft:jigsaw",
                    List.of("minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:snowy_slopes"),
                    "beard_thin", "enchantress_footsteps_igloo", 36, 16, 1647382910, 1.0F, ""),
            new Reg("enchantress_footsteps_mushroom_fields", "minecraft:jigsaw", List.of("minecraft:mushroom_fields"),
                    "beard_thin", "enchantress_footsteps_mushroom_fields", 36, 16, 1248316571, 1.0F, ""),
            new Reg("enchantress_footsteps_oasis", "minecraft:jigsaw", List.of("minecraft:desert"),
                    "beard_thin", "enchantress_footsteps_oasis", 36, 16, 1248316574, 1.0F, ""),
            new Reg("enchantress_footsteps_outpost", "minecraft:jigsaw",
                    List.of("minecraft:desert", "minecraft:plains", "minecraft:savanna", "minecraft:snowy_plains",
                            "minecraft:taiga", "#minecraft:is_mountain", "minecraft:grove"),
                    "beard_thin", "enchantress_footsteps_outpost", 36, 16, 1517026238, 0.2F, "minecraft:villages@10"),
            new Reg("enchantress_footsteps_basalt_forge", NS + "optional_mod_template", List.of("minecraft:basalt_deltas"),
                    "beard_thin", "enchantress_footsteps_basalt_forge", 32, 12, 1735687963, 1.0F, NETHER_COMPLEXES),
            new Reg("enchantress_footsteps_crimson_cabin", NS + "optional_mod_template", List.of("minecraft:crimson_forest"),
                    "beard_thin", "enchantress_footsteps_crimson_cabin", 32, 12, 1735687962, 1.0F,
                    NETHER_COMPLEXES + "," + FALLEN_SANCTUM),
            new Reg("enchantress_footsteps_soulvalley_nook", NS + "optional_mod_template", List.of("minecraft:soul_sand_valley"),
                    "beard_thin", "enchantress_footsteps_soulvalley_nook", 32, 12, 1735687965, 1.0F, NETHER_COMPLEXES),
            new Reg("enchantress_footsteps_warped_perch", NS + "optional_mod_template", List.of("minecraft:warped_forest"),
                    "beard_thin", "enchantress_footsteps_warped_perch", 32, 12, 1735687964, 1.0F, NETHER_COMPLEXES),
            new Reg("fairy_maid_cafe", NS + "land_jigsaw",
                    List.of("minecraft:cherry_grove", "minecraft:bamboo_jungle", "minecraft:meadow",
                            "minecraft:sparse_jungle", "minecraft:flower_forest"),
                    "beard_box", "fairy_maid_cafe_set", 35, 18, 1856473920, 1.0F, ""),
            new Reg("fallen_sanctum", NS + "fallen_sanctum", List.of("minecraft:crimson_forest"), "beard_box",
                    "fallen_sanctum_set", 32, 16, 1735687906, 1.0F, ""),
            new Reg("hidden_cherry_tree", NS + "land_jigsaw", List.of("minecraft:cherry_grove"), "beard_box",
                    "hidden_cherry_tree_set", 13, 10, 1856473921, 0.8F, ""),
            new Reg("hidden_retreat", NS + "hidden_retreat", List.of("minecraft:cherry_grove"), "beard_box",
                    "hidden_retreat_set", 10, 2, 1492837521, 1.0F, ""),
            new Reg("relic_sanctum", NS + "relic_sanctum", List.of("minecraft:windswept_forest"), "beard_box",
                    "relic_sanctum_set", 28, 14, 1735687904, 1.0F, ""),
            new Reg("starfall_garden", NS + "starfall_garden",
                    List.of("#minecraft:is_overworld"), "none", "starfall_garden_set", 12, 6, 20130426, 1.0F, ""),
            new Reg("starwatch_tower", NS + "starwatch_tower", List.of("minecraft:end_highlands"), "beard_thin",
                    "starwatch_tower_set", 23, 12, 1472905386, 1.0F, ""),
            new Reg("stellar_endshore", NS + "stellar_endshore", List.of("minecraft:end_barrens", "minecraft:small_end_islands"), "none",
                    "stellar_endshore_set", 512, 448, 826719453, 1.0F, ""),
            new Reg("woods_perch", "minecraft:jigsaw", List.of("minecraft:forest", "minecraft:flower_forest"),
                    "beard_thin", "woods_perch", 36, 16, 1248316572, 1.0F, ""),
            new Reg("yin_yang_altar", "minecraft:jigsaw", List.of("minecraft:dark_forest", "minecraft:swamp"),
                    "beard_box", "yin_yang_altar_set", 40, 20, 1642892357, 1.0F, ""));

    /** 各维度可能生成的本模组结构集（群系源与结构群系有交集的集合）。 */
    private static final Map<String, Set<String>> DIMENSION_SETS = Map.of(
            OVERWORLD, Set.of("cherry_cabin", "elven_realm_set", "enchantress_footsteps_graveyard", "enchantress_footsteps_igloo",
                    "enchantress_footsteps_mushroom_fields", "enchantress_footsteps_oasis", "enchantress_footsteps_outpost", "fairy_maid_cafe_set",
                    "hidden_cherry_tree_set", "hidden_retreat_set", "relic_sanctum_set", "starfall_garden_set", "woods_perch",
                    "yin_yang_altar_set"),
            NETHER, Set.of("fallen_sanctum_set", "enchantress_footsteps_basalt_forge", "enchantress_footsteps_crimson_cabin",
                    "enchantress_footsteps_soulvalley_nook", "enchantress_footsteps_warped_perch"),
            END, Set.of("starwatch_tower_set", "stellar_endshore_set"),
            // #minecraft:is_mountain 含樱花林，前哨的结构集也在归隐之地的候选里（由白名单挡住）
            RETREAT, Set.of("cherry_cabin", "enchantress_footsteps_outpost", "fairy_maid_cafe_set", "hidden_cherry_tree_set", "hidden_retreat_set",
                    "starfall_garden_set"));

    /** "不应生成在水面/水底"的检查：不检查、必须通过、已知缺陷（required=false）。 */
    private enum Dry { NONE, REQUIRED, KNOWN_DEFECT }

    /** 自然抽样的结构。隐世之境只在归隐之地由玩家搜索生成，另见 integratedServer 场景。 */
    private record Natural(String path, String dimension, Dry dry) {
    }

    private static final List<Natural> NATURAL = List.of(
            new Natural("hidden_cherry_tree", OVERWORLD, Dry.REQUIRED),
            // avoid_water 只看区块中心一列，占地边缘可以压在水上
            new Natural("fairy_maid_cafe", OVERWORLD, Dry.REQUIRED),
            new Natural("yin_yang_altar", OVERWORLD, Dry.NONE),
            // 与精灵秘境同一套 3×3 区块水域检查
            new Natural("relic_sanctum", OVERWORLD, Dry.REQUIRED),
            new Natural("fallen_sanctum", NETHER, Dry.NONE),
            // 水域检查只覆盖 3×3 区块，结构本身远大于这个范围
            new Natural("elven_realm", OVERWORLD, Dry.KNOWN_DEFECT),
            new Natural("woods_perch", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_mushroom_fields", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_igloo", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_outpost", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_graveyard", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_oasis", OVERWORLD, Dry.NONE));

    /** 真实生成后检查不悬空、不陷进地里的结构。 */
    private static final List<Natural> GROUNDED = List.of(
            // 可选模组模板结构自己找落点、自己设地形适配基准
            new Natural("cherry_cabin", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_basalt_forge", NETHER, Dry.NONE),
            new Natural("enchantress_footsteps_crimson_cabin", NETHER, Dry.NONE),
            new Natural("enchantress_footsteps_soulvalley_nook", NETHER, Dry.NONE),
            new Natural("enchantress_footsteps_warped_perch", NETHER, Dry.NONE),
            // 拼图起点只按中心一列投影到地表，坡地边缘地形适配补不满
            new Natural("fairy_maid_cafe", OVERWORLD, Dry.NONE));
    /** 悬空检查抽样的起点数。 */
    private static final int GROUNDED_STARTS = 16;

    /** "不在水里"检查抽样的起点数。 */
    private static final int DRY_STARTS = 4;

    /** 自然抽样的网格半径（放置网格格数）与每个结构检查的起点数。 */
    private static final int SAMPLE_RADIUS = 64;
    private static final int SAMPLE_STARTS = 2;
    /** 文档"不应生成在水里"：结构占地内水柱比例上限。 */
    private static final double MAX_WATER_FRACTION = 0.10;
    /** 下界顶层基岩最低 y。 */
    private static final int NETHER_ROOF_MIN_Y = 123;

    private static final String VILLAGE_SUPPLEMENT = NS + "enchantress_footsteps/village/";

    /** 本项不需要等区块，直接处理下一项。 */
    private static final CompletableFuture<Boolean> NEXT = CompletableFuture.completedFuture(false);

    private NaturalScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(scene("natural.registration", 20, NaturalScenes::registration));
        scenes.add(scene("natural.dimensionSets", 20, NaturalScenes::dimensionSets));
        scenes.add(scene("natural.hiddenRetreat.notInOverworld", 1200, NaturalScenes::hiddenRetreatNotInOverworld));
        scenes.add(scene("natural.hiddenRetreat.locateRejectedOutsideRetreat", 20, NaturalScenes::locateRejected));
        scenes.add(scene("natural.retreat.whitelist", 1200, NaturalScenes::retreatWhitelist));
        scenes.add(scene("natural.retreat.ownTerrain", 20, NaturalScenes::retreatOwnTerrain));
        scenes.add(scene("natural.hiddenRetreat.needsRealGround", 20, NaturalScenes::hiddenRetreatNeedsGround));
        for (Natural natural : NATURAL) {
            scenes.add(scene("natural." + natural.path() + ".found", 2400, ctx -> found(ctx, natural)));
            switch (natural.dry()) {
                case REQUIRED -> scenes.add(scene("natural." + natural.path() + ".notInWater", 2400,
                        ctx -> notInWater(ctx, natural)));
                case KNOWN_DEFECT -> scenes.add(scene("knownDefect." + camel(natural.path()) + "NotInWater", 2400,
                        ctx -> notInWater(ctx, natural)).withRequired(false));
                case NONE -> {
                }
            }
        }
        // 地形检查用深度函数估算地表，在山地与真实地形偏差很大（已知缺陷）
        scenes.add(scene("knownDefect.hiddenRetreatTerrainCheckMatchesRealTerrain", 1200,
                ctx -> terrainCheck(ctx, RETREAT, "hidden_retreat", HiddenRetreatStructure.class, 2, 0)).withRequired(false));
        // 精灵秘境与圣遗礼拜堂同为 relic_sanctum 类型，共用这段地形检查
        scenes.add(scene("knownDefect.relicSanctumTerrainCheckMatchesRealTerrain", 1200,
                ctx -> terrainCheck(ctx, OVERWORLD, "relic_sanctum", RelicSanctumStructure.class, 1, 1)).withRequired(false));
        scenes.add(scene("natural.fallen_sanctum.fitsNetherHeight", 2400, NaturalScenes::fallenFitsNether));
        for (Natural natural : GROUNDED) {
            scenes.add(scene("natural." + natural.path() + ".grounded", 3600, ctx -> grounded(ctx, natural)));
        }
        scenes.add(scene("natural.village.supplementAppears", 2400, NaturalScenes::villageSupplement)
                .withRequired(false));
        return scenes;
    }

    private static Scene scene(String name, int budget, Consumer<SceneContext> body) {
        return Checks.scene(name, budget, body).withArena(false);
    }

    private static void registration(SceneContext ctx) {
        ServerLevel level = ctx.server().overworld();
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (Reg reg : REGISTRATION) {
            String id = NS + reg.path();
            Structure structure = Worldgen.structure(level, id).value();
            ctx.check(BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type()).toString()).as(id + " 类型").isEqualTo(reg.type());
            ctx.check(structure.step()).as(id + " 生成阶段").isEqualTo(GenerationStep.Decoration.SURFACE_STRUCTURES);
            ctx.check(structure.terrainAdaptation().getSerializedName()).as(id + " 地形适配").isEqualTo(reg.adaptation());
            Set<String> expected = new TreeSet<>();
            for (String entry : reg.biomes()) {
                if (entry.startsWith("#")) {
                    TagKey<Biome> tag = TagKey.create(Registries.BIOME, ResourceLocation.parse(entry.substring(1)));
                    biomeRegistry.getTagOrEmpty(tag).forEach(holder -> expected.add(holder.getRegisteredName()));
                } else {
                    expected.add(entry);
                }
            }
            Set<String> actual = new TreeSet<>();
            structure.biomes().forEach(holder -> actual.add(holder.getRegisteredName()));
            Checks.sameSet(ctx, id + " 群系", expected, actual);

            StructureSet set = Worldgen.structureSet(level, NS + reg.set()).value();
            ctx.check(set.structures().stream().map(entry -> entry.structure().unwrapKey().map(k -> k.location().toString()).orElse("") + "×" + entry.weight()).toList())
                    .as(reg.set() + " 的结构").isEqualTo(List.of(id + "×1"));
            ctx.check(set.placement() instanceof RandomSpreadStructurePlacement).as(reg.set() + " 是随机分散放置").isTrue();
            if (set.placement() instanceof RandomSpreadStructurePlacement spread) {
                ctx.check(spread.spacing()).as(reg.set() + " spacing").isEqualTo(reg.spacing());
                ctx.check(spread.separation()).as(reg.set() + " separation").isEqualTo(reg.separation());
                int salt = Reflect.call(spread, StructurePlacement.class, "salt", new Class<?>[0]);
                float frequency = Reflect.call(spread, StructurePlacement.class, "frequency", new Class<?>[0]);
                Optional<StructurePlacement.ExclusionZone> exclusion =
                        Reflect.call(spread, StructurePlacement.class, "exclusionZone", new Class<?>[0]);
                ctx.check(salt).as(reg.set() + " salt").isEqualTo(reg.salt());
                ctx.check(frequency).as(reg.set() + " frequency").isEqualTo(reg.frequency());
                String exclusions = spread instanceof MultiExclusionSpreadPlacement multi
                        ? String.join(",", multi.exclusionZones().stream()
                                .map(zone -> zone.otherSet().location() + "@" + zone.chunkCount()).toList())
                        : exclusion.map(zone -> zone.otherSet().unwrapKey().map(k -> k.location().toString()).orElse("")
                                + "@" + zone.chunkCount()).orElse("");
                ctx.check(exclusions).as(reg.set() + " 排斥区").isEqualTo(reg.exclusion());
            }
        }
    }

    private static void dimensionSets(SceneContext ctx) {
        DIMENSION_SETS.forEach((dimension, expected) -> {
            ServerLevel level = Worldgen.level(ctx, dimension);
            Set<String> actual = new TreeSet<>();
            for (Holder<StructureSet> set : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
                set.unwrapKey().map(ResourceKey::location)
                        .filter(location -> location.getNamespace().equals(MaidSpellMod.MOD_ID))
                        .ifPresent(location -> actual.add(location.getPath()));
            }
            Checks.sameSet(ctx, dimension + " 可能生成的本模组结构集", expected, actual);
        });
    }

    /**
     * 主世界有樱花林，隐世之境的结构集也在主世界的候选里；满足地形和群系条件的候选区块
     * 走完真实的 STRUCTURE_STARTS 后也不能出现隐世之境起点。
     */
    private static void hiddenRetreatNotInOverworld(SceneContext ctx) {
        ServerLevel level = ctx.server().overworld();
        Holder<Structure> structure = Worldgen.structure(level, NS + "hidden_retreat");
        List<ChunkPos> candidates = Worldgen.candidates(level,
                Worldgen.structureSet(level, NS + "hidden_retreat_set").value(), 40);
        List<String> meaningful = new ArrayList<>();
        List<String> generated = new ArrayList<>();
        probe(ctx, level, structure, candidates, true, (chunk, start) -> {
            meaningful.add(chunk.toString());
            if (start != null) {
                generated.add(chunk.toString());
            }
            return meaningful.size() >= 3;
        }, 1100, () -> {
            ctx.record("candidates", meaningful);
            ctx.check(meaningful).as("主世界中满足群系和地形条件的隐世之境候选区块").isNotEmpty();
            ctx.check(generated).as("主世界中生成了隐世之境起点的区块").isEmpty();
        });
    }

    /** 在主世界、下界、末地执行 /locate structure 隐世之境，应被拒绝为"无效结构"而不是去搜索。 */
    private static void locateRejected(SceneContext ctx) {
        for (String dimension : List.of(OVERWORLD, NETHER, END)) {
            CommandSourceStack source = ctx.server().createCommandSourceStack()
                    .withLevel(Worldgen.level(ctx, dimension)).withSuppressedOutput().withPermission(4);
            String key = "<成功执行>";
            try {
                ctx.server().getCommands().getDispatcher().execute("locate structure " + NS + "hidden_retreat", source);
            } catch (CommandSyntaxException e) {
                key = e.getRawMessage() instanceof Component component
                        && component.getContents() instanceof TranslatableContents translatable
                        ? translatable.getKey() : e.getMessage();
            }
            ctx.check(key).as(dimension + " 中 /locate 隐世之境的结果").isEqualTo("commands.locate.structure.invalid");
        }
    }

    /**
     * 归隐之地只允许白名单结构：咖啡厅的群系（樱花林）和地形都满足，但真实生成后没有起点；
     * 白名单里的隐世樱树满足条件时正常生成起点（对照组）。
     */
    private static void retreatWhitelist(SceneContext ctx) {
        ctx.check(Config.allowAllStructures).as("allowAllStructures 默认值").isFalse();
        ctx.check(new TreeSet<>(Config.allowedStructures.stream().map(ResourceLocation::toString).toList()))
                .as("归隐之地结构白名单默认值").isEqualTo(new TreeSet<>(Set.of(NS + "hidden_retreat", NS + "hidden_cherry_tree")));
        ServerLevel level = Worldgen.level(ctx, RETREAT);
        Holder<Structure> cafe = Worldgen.structure(level, NS + "fairy_maid_cafe");
        Holder<Structure> tree = Worldgen.structure(level, NS + "hidden_cherry_tree");
        List<ChunkPos> cafeCandidates = Worldgen.candidates(level, Worldgen.structureSet(level, NS + "fairy_maid_cafe_set").value(), 8);
        List<ChunkPos> treeCandidates = Worldgen.candidates(level, Worldgen.structureSet(level, NS + "hidden_cherry_tree_set").value(), 12);
        List<String> ghosts = new ArrayList<>();
        List<String> cafeStarts = new ArrayList<>();
        List<String> treeValid = new ArrayList<>();
        List<String> treeMissing = new ArrayList<>();
        probe(ctx, level, cafe, cafeCandidates, false, (chunk, start) -> {
            ghosts.add(chunk.toString());
            if (start != null) {
                cafeStarts.add(chunk.toString());
            }
            return ghosts.size() >= 3;
        }, 500, () -> probe(ctx, level, tree, treeCandidates, false, (chunk, start) -> {
            treeValid.add(chunk.toString());
            if (start == null) {
                treeMissing.add(chunk.toString());
            }
            return treeValid.size() >= 3;
        }, 500, () -> {
            ctx.record("cafeCandidates", ghosts);
            ctx.record("treeCandidates", treeValid);
            ctx.check(ghosts).as("归隐之地中满足条件的咖啡厅候选区块").isNotEmpty();
            ctx.check(cafeStarts).as("归隐之地中生成了咖啡厅起点的区块").isEmpty();
            ctx.check(treeValid).as("归隐之地中满足条件的隐世樱树候选区块").isNotEmpty();
            ctx.check(treeMissing).as("满足条件却没有生成隐世樱树起点的区块").isEmpty();
        }));
    }

    /**
     * 归隐之地用自己的一份原版地形：噪声设置是本模组的，整条密度函数引用链里没有 minecraft:overworld 开头的
     * （改主世界地形的模组会覆盖这些，并在主世界以外把它们置零）；没装这类模组时与主世界逐点同密度，老存档不会出现断层。
     */
    private static void retreatOwnTerrain(SceneContext ctx) {
        ServerLevel retreat = Worldgen.level(ctx, RETREAT);
        ServerLevel overworld = ctx.server().overworld();
        if (!(retreat.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            ctx.fail("归隐之地的生成器不是噪声生成器：" + retreat.getChunkSource().getGenerator());
            return;
        }
        ctx.check(generator.generatorSettings().unwrapKey().map(key -> key.location().toString()).orElse(""))
                .as("归隐之地的噪声设置").isEqualTo(NS + "the_retreat");

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, retreat.registryAccess());
        Registry<DensityFunction> functions = retreat.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
        Set<String> referenced = new TreeSet<>();
        List<JsonElement> pending = new ArrayList<>();
        pending.add(NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(ops, generator.generatorSettings().value()).getOrThrow());
        while (!pending.isEmpty()) {
            JsonElement element = pending.removeLast();
            if (element.isJsonObject()) {
                element.getAsJsonObject().entrySet().forEach(entry -> pending.add(entry.getValue()));
            } else if (element.isJsonArray()) {
                element.getAsJsonArray().forEach(pending::add);
            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                if (id != null && functions.containsKey(id) && referenced.add(id.toString())) {
                    pending.add(DensityFunction.DIRECT_CODEC.encodeStart(ops, functions.get(id)).getOrThrow());
                }
            }
        }
        ctx.record("densityFunctions", referenced);
        ctx.check(referenced.stream().filter(id -> id.startsWith("minecraft:overworld")).toList())
                .as("归隐之地引用的主世界密度函数").isEmpty();

        DensityFunction retreatDensity = retreat.getChunkSource().randomState().router().finalDensity();
        DensityFunction overworldDensity = overworld.getChunkSource().randomState().router().finalDensity();
        List<String> differs = new ArrayList<>();
        for (int x = -2048; x <= 2048; x += 512) {
            for (int z = -2048; z <= 2048; z += 512) {
                for (int y = -48; y <= 160; y += 16) {
                    DensityFunction.SinglePointContext point = new DensityFunction.SinglePointContext(x, y, z);
                    double expected = overworldDensity.compute(point);
                    double actual = retreatDensity.compute(point);
                    if (expected != actual) {
                        differs.add(x + "," + y + "," + z + " " + expected + "≠" + actual);
                    }
                }
            }
        }
        ctx.check(differs).as("归隐之地与主世界最终密度不同的位置").isEmpty();
    }

    /**
     * 隐世之境在 depth 估算的地表下方用最终密度确认有实心地面：地形被置零（处处一样平、实际全是水）时拒绝，
     * 估算偏高几格时仍能在下方找到地面。
     */
    private static void hiddenRetreatNeedsGround(SceneContext ctx) {
        Class<?>[] signature = {DensityFunction.class, int.class, int.class, int.class};
        boolean onWater = Reflect.call(null, HiddenRetreatStructure.class, "hasGroundBelow", signature,
                DensityFunctions.zero(), 0, 80, 0);
        ctx.check(onWater).as("最终密度处处为零时有地面").isFalse();
        // y=76 及以下为实心
        DensityFunction ground = DensityFunctions.yClampedGradient(76, 77, 1, -1);
        boolean estimateHigh = Reflect.call(null, HiddenRetreatStructure.class, "hasGroundBelow", signature, ground, 0, 80, 0);
        ctx.check(estimateHigh).as("估算地表比实际高 3 格时有地面").isTrue();
        boolean estimateTooHigh = Reflect.call(null, HiddenRetreatStructure.class, "hasGroundBelow", signature, ground, 0, 82, 0);
        ctx.check(estimateTooHigh).as("估算地表比实际高 5 格时有地面").isFalse();
    }

    /** 固定种子世界里由近及远找到结构的真实起点（走 STRUCTURE_STARTS），检查起点在建筑高度范围内。 */
    private static void found(SceneContext ctx, Natural natural) {
        ServerLevel level = Worldgen.level(ctx, natural.dimension());
        sampleStarts(ctx, level, natural.path(), SAMPLE_STARTS, starts -> {
            List<String> summary = new ArrayList<>();
            for (StructureStart start : starts) {
                BoundingBox box = start.getBoundingBox();
                String where = start.getChunkPos() + " " + box;
                summary.add(where + " pieces=" + start.getPieces().size());
                // 下界的高度由 natural.fallen_sanctum.fitsNetherHeight 单独检查
                if (!natural.dimension().equals(NETHER)) {
                    ctx.check(box.minY() >= level.getMinBuildHeight() && box.maxY() < level.getMaxBuildHeight())
                            .as(where + " 在建筑高度范围内").isTrue();
                }
            }
            ctx.record("starts", summary);
        });
    }

    /** 可选地形观测：建筑本体占地内的水柱比例不超过 10%。 */
    private static void notInWater(SceneContext ctx, Natural natural) {
        ServerLevel level = Worldgen.level(ctx, natural.dimension());
        sampleStarts(ctx, level, natural.path(), DRY_STARTS, starts -> {
            List<String> summary = new ArrayList<>();
            for (StructureStart start : starts) {
                BoundingBox box = actualPieceBox(start);
                double water = Worldgen.waterFraction(level, box, 4);
                String where = start.getChunkPos() + " " + box;
                summary.add(where + String.format(" water=%.2f", water));
                ctx.check(water).as(where + " 占地内水柱比例").isAtMost(MAX_WATER_FRACTION);
            }
            ctx.record("starts", summary);
        });
    }

    private static String camel(String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("_")) {
            out.append(out.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return out.toString();
    }

    /**
     * 堕天圣堂的拼图片须位于世界底部与下界顶层基岩之间；地形适配范围不算建筑本体。
     */
    private static void fallenFitsNether(SceneContext ctx) {
        ServerLevel level = Worldgen.level(ctx, NETHER);
        sampleStarts(ctx, level, "fallen_sanctum", 4, starts -> {
            List<String> summary = new ArrayList<>();
            for (StructureStart start : starts) {
                BoundingBox box = actualPieceBox(start);
                String where = start.getChunkPos() + " " + box;
                summary.add(where + " height=" + box.getYSpan());
                ctx.check(box.maxY()).as(where + " 最高点（下界顶层基岩最低 y=" + NETHER_ROOF_MIN_Y + "）")
                        .isLessThan(NETHER_ROOF_MIN_Y);
                ctx.check(box.minY()).as(where + " 最低点（世界底部 y=" + level.getMinBuildHeight() + "）")
                        .isAtLeast(level.getMinBuildHeight());
            }
            ctx.record("starts", summary);
        });
    }

    /**
     * 抽样的起点所在区块完整生成后，模板最底层的每个实心方块和植物正下方都是实心方块：
     * 不压在洞穴的空处、岩浆或悬崖边上，地形适配也没有把地面挖掉或漏补。
     * 也不与别的结构（如下界要塞）的拼图块重叠，否则后放置的结构会把地板和脚下的地面切开。
     * 建筑外一圈的地形不高过地板，否则墙和门会埋进地里。
     * 可选模组模板结构的底层有空格或植物时坐在地面上，不顶替自然地面那一层；挡住地形地物的结构占地内没有玄武岩石柱林、玄武岩柱、三角洲和涌泉。
     */
    private static void grounded(SceneContext ctx, Natural natural) {
        ServerLevel level = Worldgen.level(ctx, natural.dimension());
        sampleStarts(ctx, level, natural.path(), GROUNDED_STARTS, starts -> {
            Set<ChunkPos> chunks = new LinkedHashSet<>();
            for (StructureStart start : starts) {
                BoundingBox box = actualPieceBox(start);
                ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4))
                        .forEach(chunks::add);
            }
            CompletableFuture<Void> loaded = Worldgen.loadFull(ctx, level, chunks);
            ctx.await(loaded::isDone).within(1100).then(() -> {
                loaded.join();
                List<String> summary = new ArrayList<>();
                StructurePieceSerializationContext serialization = StructurePieceSerializationContext.fromLevel(level);
                for (StructureStart start : starts) {
                    Footing footing = footing(level, start);
                    String where = start.getChunkPos() + " " + actualPieceBox(start);
                    List<String> buried = buried(level, start);
                    List<String> subsurface = start.getStructure() instanceof OptionalModTemplateStructure
                            ? openBaseBelowGround(level, start) : List.of();
                    boolean checkFeatures = level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(start.getStructure()).is(TerrainFeatureGuard.TAG);
                    List<String> features = checkFeatures ? terrainFeatures(level, start) : List.of();
                    summary.add(where + " 悬空=" + footing.floating().size() + " 与其他结构重叠=" + footing.overlapped().size()
                            + " 埋入=" + buried.size()
                            + (checkFeatures ? " 玄武岩柱与熔岩=" + features.size() : ""));
                    ctx.check(subsurface).as(where + " 底层有空格或植物却顶替了自然地面的拼图块").isEmpty();
                    ctx.check(features).as(where + " 长进占地的玄武岩石柱林、玄武岩柱、三角洲和涌泉").isEmpty();
                    // 魂谷一隅和原版化石一样不带地基，底下悬空不算
                    if (!natural.path().equals("enchantress_footsteps_soulvalley_nook")) {
                        ctx.check(footing.floating()).as(where + " 底层下方不是实心方块的位置").isEmpty();
                    }
                    ctx.check(footing.overlapped()).as(where + " 底层下方被其他结构切开的位置").isEmpty();
                    ctx.check(buried).as(where + " 建筑外一圈高过地板的地形").isEmpty();
                    // 起点在放置前要随区块存盘再读回
                    CompoundTag saved = start.createTag(serialization, start.getChunkPos());
                    StructureStart reloaded = StructureStart.loadStaticStart(serialization, saved, level.getSeed());
                    ctx.check(reloaded == null ? null : reloaded.createTag(serialization, start.getChunkPos()))
                            .as(where + " 存盘再读回后的起点").isEqualTo(saved);
                }
                ctx.record("starts", summary);
            });
        });
    }

    /**
     * 模板最底层的实心方块和植物中，正下方不是实心方块的世界坐标（附下方方块与往下空了几格，超过 16 格记为 16+），
     * 以及其中落在别的结构拼图块里的位置。下界的可选模组模板结构连最底层的空格也算：院子底下不能是空的。
     */
    private record Footing(List<String> floating, List<String> overlapped) {
    }

    private static Footing footing(ServerLevel level, StructureStart start) {
        List<String> floating = new ArrayList<>();
        List<String> overlapped = new ArrayList<>();
        boolean everyCell = level.dimensionType().ultraWarm() && start.getStructure() instanceof OptionalModTemplateStructure;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            for (StructureTemplate.StructureBlockInfo cell : baseLayer(level, pool)) {
                if (cell.state().isAir() && !everyCell) {
                    continue;
                }
                BlockPos pos = cell.pos();
                BlockState below = level.getBlockState(pos.below());
                if (!below.blocksMotion()) {
                    int gap = 1;
                    while (gap <= 16 && !level.getBlockState(pos.below(gap + 1)).blocksMotion()) {
                        gap++;
                    }
                    String where = pos.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath()
                            + "×" + (gap > 16 ? "16+" : gap);
                    Optional<String> other = otherStructureAt(level, start, pos);
                    if (other.isPresent()) {
                        overlapped.add(where + " " + other.get());
                    } else {
                        floating.add(where);
                    }
                }
            }
        }
        return new Footing(floating, overlapped);
    }

    /** 拼图块模板最底层（模板内 y=0）的每一格，坐标换成世界坐标；不是单模板元素时为空。 */
    private static List<StructureTemplate.StructureBlockInfo> baseLayer(ServerLevel level, PoolElementStructurePiece pool) {
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>();
        if (!(pool.getElement() instanceof SinglePoolElement single)) {
            return out;
        }
        StructureTemplate template = level.getStructureManager().getOrCreate(ResourceLocation.parse(Worldgen.templateId(single)));
        List<StructureTemplate.Palette> palettes = Reflect.field(template, StructureTemplate.class, "palettes");
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
        for (StructureTemplate.StructureBlockInfo info : palettes.getFirst().blocks()) {
            if (info.pos().getY() == 0) {
                out.add(new StructureTemplate.StructureBlockInfo(
                        StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(pool.getPosition()), info.state(), null));
            }
        }
        return out;
    }

    /**
     * 模板最底层有空格或植物、却没有坐在地面上（groundLevelDelta 不为 0，最底层顶替了自然地面那一层）的拼图块：
     * 空格和植物底下露出的是地表下面的方块（绯红森林里露出地狱岩）。附空格和植物的格数。
     */
    private static List<String> openBaseBelowGround(ServerLevel level, StructureStart start) {
        List<String> out = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool) || pool.getGroundLevelDelta() == 0) {
                continue;
            }
            long open = baseLayer(level, pool).stream().filter(cell -> !cell.state().blocksMotion()).count();
            if (open > 0) {
                out.add(pool.getPosition().toShortString() + " groundLevelDelta=" + pool.getGroundLevelDelta() + " 空格和植物=" + open);
            }
        }
        return out;
    }

    /**
     * 占地水平外扩 2 格的范围内，地物长进来的玄武岩、熔岩源和岩浆块，按列附第一格的位置和种类。
     * 地面之上（底框最低 y + groundLevelDelta 往上）：既不是模板放的、也不是噪声地形本来就有的；
     * 地板下面那一层只看占地正下方的熔岩源，那里是地基或自然地面，本来就该是实心的；岩浆块矿脉会长进这一层的下界岩，不算。
     * 地面以下再往深处是地形适配填的，噪声地形里没有，不查。流动的岩浆是外面流进来的，不算。
     */
    private static List<String> terrainFeatures(ServerLevel level, StructureStart start) {
        List<String> out = new ArrayList<>();
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool) || !(pool.getElement() instanceof SinglePoolElement single)) {
                continue;
            }
            BoundingBox box = pool.getBoundingBox();
            StructureTemplate template = level.getStructureManager().getOrCreate(ResourceLocation.parse(Worldgen.templateId(single)));
            List<StructureTemplate.Palette> palettes = Reflect.field(template, StructureTemplate.class, "palettes");
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
            java.util.Map<BlockPos, Block> templateBlocks = new java.util.HashMap<>();
            for (StructureTemplate.StructureBlockInfo info : palettes.getFirst().blocks()) {
                templateBlocks.put(StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(pool.getPosition()),
                        info.state().getBlock());
            }
            int floor = box.minY() + pool.getGroundLevelDelta();
            for (int x = box.minX() - 2; x <= box.maxX() + 2; x++) {
                for (int z = box.minZ() - 2; z <= box.maxZ() + 2; z++) {
                    net.minecraft.world.level.NoiseColumn noise = null;
                    boolean underFootprint = x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
                    int count = 0;
                    String first = null;
                    for (int y = floor - 1; y <= box.maxY() + 12; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        boolean lava = state.is(Blocks.MAGMA_BLOCK) || state.getFluidState().is(Fluids.LAVA);
                        if (!lava && !state.is(Blocks.BASALT) || templateBlocks.get(pos) == state.getBlock()) {
                            continue;
                        }
                        if (y < floor) {
                            if (!state.getFluidState().is(Fluids.LAVA) || !underFootprint) {
                                continue;
                            }
                        } else if (!box.isInside(pos)) {
                            if (noise == null) {
                                noise = generator.getBaseColumn(x, z, level, randomState);
                            }
                            if (!noise.getBlock(y).isAir()) {
                                continue;
                            }
                        }
                        count++;
                        if (first == null) {
                            first = pos.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                        }
                    }
                    if (count > 0) {
                        out.add(first + "×" + count);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 拼图块底框外紧贴的一圈列中，地板（底框最低 y + groundLevelDelta，地面之上的第一层）再往上一层被地形占住的位置，
     * 附那一格的方块与往上连着几格地形。树木、巨型菌、蜂巢和竹子不算地形。
     */
    private static List<String> buried(ServerLevel level, StructureStart start) {
        List<String> out = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            BoundingBox box = pool.getBoundingBox();
            int floor = box.minY() + pool.getGroundLevelDelta();
            for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
                for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                    int column = x;
                    int row = z;
                    if (start.getPieces().stream().anyMatch(other -> other.getBoundingBox().isInside(column, floor, row))) {
                        continue;
                    }
                    BlockPos above = new BlockPos(x, floor + 1, z);
                    int height = 0;
                    while (floor + 1 + height <= box.maxY() && isTerrain(level.getBlockState(above.above(height)))) {
                        height++;
                    }
                    if (height > 0) {
                        out.add(above.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(above).getBlock()).getPath()
                                + "+" + height);
                    }
                }
            }
        }
        return out;
    }

    private static boolean isTerrain(BlockState state) {
        return state.blocksMotion() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.WART_BLOCKS)
                && !state.is(BlockTags.BEEHIVES) && !state.is(Blocks.SHROOMLIGHT) && !state.is(Blocks.BAMBOO);
    }

    /** pos 或它正下方落在别的结构的拼图块里时，那个结构的 ID。 */
    private static Optional<String> otherStructureAt(ServerLevel level, StructureStart start, BlockPos pos) {
        for (BlockPos probe : List.of(pos, pos.below())) {
            StructureStart other = level.structureManager().getStructureWithPieceAt(probe, holder -> holder.value() != start.getStructure());
            if (other.isValid()) {
                return Optional.of(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(other.getStructure()).toString());
            }
        }
        return Optional.empty();
    }

    private static BoundingBox actualPieceBox(StructureStart start) {
        return BoundingBox.encapsulatingBoxes(start.getPieces().stream()
                .map(net.minecraft.world.level.levelgen.structure.StructurePiece::getBoundingBox).toList()).orElseThrow();
    }

    /** 按放置网格由近及远抽样 count 个真实起点；满足生成条件却没有起点的候选、一个都没找到都记为失败。 */
    private static void sampleStarts(SceneContext ctx, ServerLevel level, String path, int count,
                                     Consumer<List<StructureStart>> then) {
        String id = NS + path;
        Holder<Structure> structure = Worldgen.structure(level, id);
        String setId = REGISTRATION.stream().filter(reg -> reg.path().equals(path)).findFirst().orElseThrow().set();
        List<ChunkPos> candidates = Worldgen.candidates(level, Worldgen.structureSet(level, NS + setId).value(), SAMPLE_RADIUS);
        List<StructureStart> starts = new ArrayList<>();
        List<String> validWithoutStart = new ArrayList<>();
        probe(ctx, level, structure, candidates, true, (chunk, start) -> {
            if (start == null) {
                validWithoutStart.add(chunk.toString());
            } else {
                starts.add(start);
            }
            return starts.size() >= count;
        }, 2300, () -> {
            ctx.check(starts).as(level.dimension().location() + " 中 " + SAMPLE_RADIUS + " 格放置网格内找到的 " + id).isNotEmpty();
            ctx.check(validWithoutStart).as("满足生成条件却没有起点的区块").isEmpty();
            then.accept(starts);
        });
    }

    /**
     * 逐个候选区块：满足生成条件的（checkBiome 时先按区块中心群系粗筛）走真实 STRUCTURE_STARTS，
     * 把区块和起点（没有为 null）交给 onCandidate，它返回 true 时提前结束。
     */
    private static void probe(SceneContext ctx, ServerLevel level, Holder<Structure> structure, List<ChunkPos> candidates,
                              boolean checkBiome, BiPredicate<ChunkPos, StructureStart> onCandidate, int maxTicks,
                              Runnable done) {
        Batch.runAsync(ctx, candidates, chunk -> {
            if ((checkBiome && !biomeMayMatch(level, structure, chunk))
                    || Worldgen.validPoint(level, structure.value(), chunk).isEmpty()) {
                return NEXT;
            }
            return Worldgen.startAt(level, structure.value(), chunk).thenApply(start -> onCandidate.test(chunk, start));
        }, maxTicks, done);
    }

    /** 候选区块中心的群系（y 取该列地表）是否可能满足结构，用来在昂贵的拼图组装前筛掉候选。 */
    private static boolean biomeMayMatch(ServerLevel level, Holder<Structure> structure, ChunkPos chunk) {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int y = level.dimensionType().hasCeiling() ? 64 : Worldgen.height(level, x, z, Heightmap.Types.WORLD_SURFACE_WG);
        return structure.value().biomes().contains(Worldgen.biomeAt(level, x, y, z));
    }

    /**
     * 已知缺陷：自定义地形检查的准确性。在一片区块网格上运行结构自己的地形检查（深度函数估算），
     * 对通过的位置用真实噪声高度图复核——同一片区域的真实水柱比例、高度方差，以及起点高度与真实地表的偏差。
     * @param range 结构检查的区块半径（隐世之境 2，圣遗礼拜堂 1）
     * @param startOffset 起点 y 相对估算地表的偏移（隐世之境 0，圣遗礼拜堂 +1）
     */
    private static void terrainCheck(SceneContext ctx, String dimension, String path, Class<? extends Structure> type,
                                     int range, int startOffset) {
        ServerLevel level = Worldgen.level(ctx, dimension);
        Structure structure = Worldgen.structure(level, NS + path).value();
        List<ChunkPos> grid = new ArrayList<>();
        IntStream.rangeClosed(-8, 8).forEach(i -> IntStream.rangeClosed(-8, 8).forEach(j -> grid.add(new ChunkPos(i * 20 + 3, j * 20 + 7))));
        List<String> accepted = new ArrayList<>();
        List<String> wet = new ArrayList<>();
        List<String> rough = new ArrayList<>();
        List<String> offSurface = new ArrayList<>();
        Batch.run(ctx, grid, chunk -> {
            Structure.GenerationContext context = Worldgen.context(level, chunk, holder -> true);
            OptionalInt terrain = Reflect.call(structure, type, "checkTerrainAndGetHeight",
                    new Class<?>[]{Structure.GenerationContext.class, ChunkPos.class}, context, chunk);
            if (terrain.isEmpty()) {
                return false;
            }
            int centerX = chunk.getMinBlockX() + 8;
            int centerZ = chunk.getMinBlockZ() + 8;
            int estimate = Reflect.call(null, type, "estimateSurfaceHeight",
                    new Class<?>[]{DensityFunction.class, int.class, int.class, Structure.GenerationContext.class},
                    context.randomState().router().depth(), centerX, centerZ, context);
            int startY = estimate + startOffset;
            int groundTop = Worldgen.height(level, centerX, centerZ, Heightmap.Types.OCEAN_FLOOR_WG) - 1;
            int water = 0;
            int total = 0;
            double sum = 0;
            double sumSquares = 0;
            for (int x = (chunk.x - range) * 16; x < (chunk.x + range + 1) * 16; x += 8) {
                for (int z = (chunk.z - range) * 16; z < (chunk.z + range + 1) * 16; z += 8) {
                    int floor = Worldgen.height(level, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
                    if (Worldgen.height(level, x, z, Heightmap.Types.WORLD_SURFACE_WG) > floor) {
                        water++;
                    }
                    total++;
                    sum += floor;
                    sumSquares += (double) floor * floor;
                }
            }
            double mean = sum / total;
            double variance = sumSquares / total - mean * mean;
            double waterFraction = (double) water / total;
            String where = chunk + String.format(" 起点y=%d 真实地表顶=%d 方差=%.1f 水=%.2f", startY, groundTop, variance, waterFraction);
            accepted.add(where);
            if (waterFraction > MAX_WATER_FRACTION) {
                wet.add(where);
            }
            if (variance > 32) {
                rough.add(where);
            }
            if (Math.abs(estimate - groundTop) > 3) {
                offSurface.add(where);
            }
            return false;
        }, 1100, () -> {
            ctx.record("grid", grid.size());
            ctx.record("accepted", accepted.size());
            ctx.record("acceptedSample", accepted.stream().limit(8).toList());
            ctx.check(accepted).as("网格中通过地形检查的位置").isNotEmpty();
            ctx.check(wet).as("通过检查但真实水柱比例超过 10% 的位置").isEmpty();
            ctx.check(rough).as("通过检查但真实高度方差超过 32 的位置").isEmpty();
            ctx.check(offSurface).as("估算地表与真实地表相差超过 3 格的位置").isEmpty();
        });
    }

    /** 固定种子主世界里抽样的村庄中，本模组的补充街道和经由它接入的补充房屋都至少出现一次。 */
    private static void villageSupplement(SceneContext ctx) {
        ServerLevel level = ctx.server().overworld();
        StructureSet villages = Worldgen.structureSet(level, "minecraft:villages").value();
        List<ChunkPos> candidates = Worldgen.candidates(level, villages, 24);
        List<String> sampled = new ArrayList<>();
        List<String> withStreet = new ArrayList<>();
        List<String> withHouse = new ArrayList<>();
        Batch.runAsync(ctx, candidates, chunk -> Worldgen.startsAt(level, chunk).thenApply(starts -> {
            for (StructureSet.StructureSelectionEntry entry : villages.structures()) {
                StructureStart start = starts.get(entry.structure().value());
                if (start == null) {
                    continue;
                }
                String where = entry.structure().unwrapKey().map(k -> k.location().getPath()).orElse("?") + "@" + chunk;
                sampled.add(where);
                List<String> templates = Worldgen.templates(start);
                if (templates.stream().anyMatch(t -> t.startsWith(VILLAGE_SUPPLEMENT) && t.endsWith("_street"))) {
                    withStreet.add(where);
                }
                if (templates.stream().anyMatch(t -> t.startsWith(VILLAGE_SUPPLEMENT) && t.endsWith("_house"))) {
                    withHouse.add(where);
                }
            }
            return sampled.size() >= 40;
        }), 2300, () -> {
            ctx.record("villages", sampled.size());
            ctx.record("withStreet", withStreet);
            ctx.record("withHouse", withHouse);
            ctx.check(sampled.size()).as("抽样到的村庄数").isGreaterThan(0);
            ctx.check(withStreet).as("出现补充街道的村庄").isNotEmpty();
            ctx.check(withHouse).as("出现补充房屋的村庄").isNotEmpty();
        });
    }
}
