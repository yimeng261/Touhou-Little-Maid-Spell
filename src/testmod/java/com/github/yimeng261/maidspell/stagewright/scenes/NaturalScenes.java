package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Batch;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import com.github.yimeng261.maidspell.worldgen.structure.RelicSanctumStructure;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
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
 * 隐世之境只在归隐之地生成、归隐之地结构白名单、自定义地形检查的准确性、村庄补充的自然出现。
 * <p>不借用场地，直接查询服务器的主世界/下界/末地/归隐之地；计算只到 STRUCTURE_STARTS 或只用噪声，不生成方块。
 */
public final class NaturalScenes {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private static final String RETREAT = NS + "the_retreat";

    /** 结构注册快照（来自当前数据）：类型、群系条目（# 开头为标签）、地形适配、所属结构集与随机分散放置参数。 */
    private record Reg(String path, String type, List<String> biomes, String adaptation, String set,
                       int spacing, int separation, int salt, float frequency, String exclusion) {
    }

    private static final List<Reg> REGISTRATION = List.of(
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
                    List.of("#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle", "#minecraft:is_savanna",
                            "#minecraft:is_hill", "#minecraft:is_mountain", "#minecraft:is_beach", "minecraft:plains",
                            "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:cherry_grove", "minecraft:snowy_plains",
                            "minecraft:desert", "minecraft:swamp"),
                    "none", "starfall_garden_set", 24, 12, 20130426, 1.0F, ""),
            new Reg("starwatch_tower", NS + "starwatch_tower", List.of("minecraft:end_highlands"), "beard_thin",
                    "starwatch_tower_set", 23, 12, 1472905386, 1.0F, ""),
            new Reg("stellar_endshore", NS + "stellar_endshore", List.of("minecraft:small_end_islands"), "none",
                    "stellar_endshore_set", 512, 448, 826719453, 1.0F, ""),
            new Reg("woods_perch", "minecraft:jigsaw", List.of("minecraft:forest", "minecraft:flower_forest"),
                    "beard_thin", "woods_perch", 36, 16, 1248316572, 1.0F, ""),
            new Reg("yin_yang_altar", "minecraft:jigsaw", List.of("minecraft:dark_forest", "minecraft:swamp"),
                    "beard_box", "yin_yang_altar_set", 40, 20, 1642892357, 1.0F, ""));

    /** 各维度可能生成的本模组结构集（群系源与结构群系有交集的集合）。 */
    private static final Map<String, Set<String>> DIMENSION_SETS = Map.of(
            OVERWORLD, Set.of("elven_realm_set", "enchantress_footsteps_graveyard", "enchantress_footsteps_igloo",
                    "enchantress_footsteps_mushroom_fields", "enchantress_footsteps_oasis", "enchantress_footsteps_outpost", "fairy_maid_cafe_set",
                    "hidden_cherry_tree_set", "hidden_retreat_set", "relic_sanctum_set", "starfall_garden_set", "woods_perch",
                    "yin_yang_altar_set"),
            NETHER, Set.of("fallen_sanctum_set"),
            END, Set.of("starwatch_tower_set", "stellar_endshore_set"),
            // #minecraft:is_mountain 含樱花林，前哨的结构集也在归隐之地的候选里（由白名单挡住）
            RETREAT, Set.of("enchantress_footsteps_outpost", "fairy_maid_cafe_set", "hidden_cherry_tree_set", "hidden_retreat_set",
                    "starfall_garden_set"));

    /** 文档"不应生成在水面/水底"的检查：不检查、必须通过、已知缺陷（required=false）。 */
    private enum Dry { NONE, REQUIRED, KNOWN_DEFECT }

    /** 自然抽样的结构。隐世之境只在归隐之地由玩家搜索生成，另见 integratedServer 场景。 */
    private record Natural(String path, String dimension, Dry dry) {
    }

    private static final List<Natural> NATURAL = List.of(
            new Natural("hidden_cherry_tree", OVERWORLD, Dry.REQUIRED),
            // avoid_water 只看区块中心一列，占地边缘可以压在水上
            new Natural("fairy_maid_cafe", OVERWORLD, Dry.KNOWN_DEFECT),
            new Natural("yin_yang_altar", OVERWORLD, Dry.NONE),
            // 与精灵秘境同一套 3×3 区块水域检查
            new Natural("relic_sanctum", OVERWORLD, Dry.KNOWN_DEFECT),
            new Natural("fallen_sanctum", NETHER, Dry.NONE),
            // 水域检查只覆盖 3×3 区块，结构本身远大于这个范围
            new Natural("elven_realm", OVERWORLD, Dry.KNOWN_DEFECT),
            new Natural("woods_perch", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_mushroom_fields", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_igloo", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_outpost", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_graveyard", OVERWORLD, Dry.NONE),
            new Natural("enchantress_footsteps_oasis", OVERWORLD, Dry.NONE));

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
        scenes.add(scene("knownDefect.fallenSanctumFitsNetherHeight", 2400, NaturalScenes::fallenFitsNether)
                .withRequired(false));
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
                ctx.check(exclusion.map(zone -> zone.otherSet().unwrapKey().map(k -> k.location().toString()).orElse("")
                        + "@" + zone.chunkCount()).orElse("")).as(reg.set() + " 排斥区").isEqualTo(reg.exclusion());
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

    /** 固定种子世界里由近及远找到结构的真实起点（走 STRUCTURE_STARTS），检查起点在建筑高度范围内。 */
    private static void found(SceneContext ctx, Natural natural) {
        ServerLevel level = Worldgen.level(ctx, natural.dimension());
        sampleStarts(ctx, level, natural.path(), SAMPLE_STARTS, starts -> {
            List<String> summary = new ArrayList<>();
            for (StructureStart start : starts) {
                BoundingBox box = start.getBoundingBox();
                String where = start.getChunkPos() + " " + box;
                summary.add(where + " pieces=" + start.getPieces().size());
                // 下界的高度问题见 knownDefect.fallenSanctumFitsNetherHeight
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
