package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.data.StructureSnapshots;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Fidelity;
import com.github.yimeng261.maidspell.stagewright.support.StructureStage;
import com.github.yimeng261.maidspell.stagewright.support.WorldExtract;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 结构内容场景：每个结构按"方块 / 方块实体 / 容器 / 实体 / 女仆"各一个场景，外加村庄补充房屋和已知缺陷。
 * <p>每个场景各自在超平坦场地放置一次结构（场地互相独立，不能共享放置）。
 */
public final class StructureScenes {
    /** 结构 ID（本模组命名空间下的路径）、强制加载区块半径、是否带结构女仆、已知会死亡或卡在方块里的实体类型。 */
    private record Spec(String path, int radius, boolean maid, Set<String> knownDead) {
        Spec(String path, int radius, boolean maid) {
            this(path, radius, maid, Set.of());
        }
    }

    private static final List<Spec> STRUCTURES = List.of(
            new Spec("hidden_retreat", 5, true),
            // 狐狸按模板原高度生成、不随重力处理器落地（已知缺陷，见 knownDefect.hiddenCherryTreeFoxOnGround）
            new Spec("hidden_cherry_tree", 2, false, Set.of("minecraft:fox")),
            new Spec("fairy_maid_cafe", 2, true),
            new Spec("yin_yang_altar", 2, false),
            new Spec("relic_sanctum", 4, true),
            new Spec("fallen_sanctum", 7, true),
            new Spec("elven_realm", 6, true),
            new Spec("woods_perch", 2, false),
            new Spec("enchantress_footsteps_mushroom_fields", 2, false),
            new Spec("enchantress_footsteps_igloo", 2, false),
            new Spec("enchantress_footsteps_outpost", 4, false),
            new Spec("enchantress_footsteps_graveyard", 2, false));

    private static final List<String> VILLAGE_BIOMES = List.of("desert", "plains", "savanna", "snowy", "taiga");

    private static final String VILLAGE = Checks.NS + "enchantress_footsteps/village/";

    /** 不逐键比对的（模板, "方块#键"）：格式变化由语义场景覆盖，已知缺陷由 required=false 的场景单独断言。 */
    private static final BiPredicate<String, String> KNOWN_BE_DEFECTS = (template, key) ->
            // Goety 1.21 的缠魂水槽固定是无限水，不读模板里的 FluidName/Amount（structure.yin_yang_altar.hauntedJugHasWater）
            template.equals("touhou_little_maid_spell:yin_yang_altar/yin_yang_altar")
                    && (key.equals("goety:haunted_jug#FluidName") || key.equals("goety:haunted_jug#Amount"));

    /** 模板里嵌在方块中的实体（已知缺陷，见 knownDefect.villagePlainsHouseChairsNotInBlocks）。 */
    private static final String CHAIR = "touhou_little_maid:chair";

    /** 模板引用了未安装模组的方块，放置后是空气（knownDefect.templatesUseOnlyRegisteredBlocks）。 */
    private static final Set<String> KNOWN_UNREGISTERED_BLOCKS = Set.of("monsters_and_girls:glow_berry_bush_unlit");

    /** 实体放置后观察多少 tick 再检查存活/卡墙。 */
    private static final int SETTLE_TICKS = 40;

    private StructureScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        for (Spec spec : STRUCTURES) {
            String structure = Checks.NS + spec.path();
            Function<SceneContext, StructureStage.Placed> place = ctx -> StructureStage.place(ctx, structure);
            addCategories(scenes, "structure." + spec.path(), spec.radius(), spec.maid(), spec.knownDead(), place);
        }
        for (String biome : VILLAGE_BIOMES) {
            String village = "minecraft:village_" + biome;
            // 街道：从补充街道池组装一层（相邻的原版街道/尽头片一并放置）
            scenes.add(structureScene("village." + biome + ".street.blocks", 3,
                    ctx -> blocks(ctx, StructureStage.placePool(ctx, village, VILLAGE + biome + "_streets", 1))));
            // 房屋：直接从房屋池放置检查内容（自然村庄里经补充街道接入，见 natural.village.supplementAppears）
            addCategories(scenes, "village." + biome + ".house", 2, false,
                    biome.equals("plains") ? Set.of(CHAIR) : Set.of(), ctx -> placeHouse(ctx, biome));
            scenes.add(Checks.scene("village." + biome + ".street.linksOwnBiome", 20, ctx -> streetLinksOwnBiome(ctx, biome))
                    // taiga_street 的街道接口指向 snowy 的街道池（已知缺陷）
                    .withRequired(!biome.equals("taiga")));
        }
        scenes.add(Checks.scene("village.poolInjection", 20, StructureScenes::poolInjection));
        scenes.add(Checks.superflat("knownDefect.hiddenCherryTreeFoxOnGround", 20, StructureScenes::foxOnGround)
                .withChunkRadius(2).withRequired(false));
        scenes.add(structureScene("structure.yin_yang_altar.hauntedJugHasWater", 2, StructureScenes::hauntedJugHasWater));
        scenes.add(structureScene("village.plains.house.cauldronKeepsInk", 2, StructureScenes::cauldronKeepsInk));
        scenes.add(structureScene("village.taiga.house.pedestalRapierKeepsData", 2, StructureScenes::rapierKeepsData));
        scenes.add(Checks.scene("knownDefect.templatesUseOnlyRegisteredBlocks", 20, StructureScenes::onlyRegisteredBlocks)
                .withRequired(false));
        scenes.add(structureScene("knownDefect.villagePlainsHouseChairsNotInBlocks", 2, StructureScenes::chairsNotInBlocks)
                .withRequired(false));
        return scenes;
    }

    private static StructureStage.Placed placeHouse(SceneContext ctx, String biome) {
        return StructureStage.placePool(ctx, "minecraft:village_" + biome, VILLAGE + biome + "_houses", 1);
    }

    /** 已放置结构里指定方块的方块实体保存结果（按模板顺序）。 */
    private static List<CompoundTag> savedBlockEntities(SceneContext ctx, StructureStage.Placed placed, String block) {
        List<CompoundTag> out = new ArrayList<>();
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureBlockInfo info : piece.blocks()) {
                if (info.nbt() != null && BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString().equals(block)) {
                    BlockEntity be = ctx.level().getBlockEntity(info.pos());
                    ctx.check(be).as(block + " 的方块实体 @" + info.pos()).isNotNull();
                    if (be != null) {
                        out.add(be.saveWithoutMetadata(ctx.level().registryAccess()));
                    }
                }
            }
        }
        ctx.check(out).as("结构中的 " + block).isNotEmpty();
        return out;
    }

    private static void addCategories(List<Scene> scenes, String prefix, int radius, boolean maid, Set<String> knownDead,
                                      Function<SceneContext, StructureStage.Placed> place) {
        scenes.add(structureScene(prefix + ".blocks", radius, ctx -> blocks(ctx, place.apply(ctx))));
        scenes.add(structureScene(prefix + ".blockEntities", radius, ctx -> blockEntities(ctx, place.apply(ctx))));
        scenes.add(structureScene(prefix + ".containers", radius, ctx -> containers(ctx, place.apply(ctx))));
        scenes.add(structureScene(prefix + ".entities", radius, ctx -> entities(ctx, place.apply(ctx), knownDead)));
        if (maid) {
            scenes.add(structureScene(prefix + ".maid", radius, ctx -> maids(ctx, place.apply(ctx))));
        }
    }

    private static Scene structureScene(String name, int radius, Consumer<SceneContext> body) {
        return Checks.superflat(name, SETTLE_TICKS + 60, body).withChunkRadius(radius);
    }

    private static void blocks(SceneContext ctx, StructureStage.Placed placed) {
        ServerLevel level = ctx.level();
        List<String> expected = WorldExtract.expected(placed).specialBlocks().stream()
                .filter(block -> !KNOWN_UNREGISTERED_BLOCKS.contains(block)).toList();
        Checks.sameSet(ctx, "特殊方块", expected, WorldExtract.specialBlocks(level, placed));
        Fidelity.blocks(ctx, level, placed);
    }

    private static void blockEntities(SceneContext ctx, StructureStage.Placed placed) {
        Fidelity.blockEntities(ctx, ctx.level(), placed, KNOWN_BE_DEFECTS);
    }

    private static void containers(SceneContext ctx, StructureStage.Placed placed) {
        Checks.sameMultiset(ctx, "容器（战利品表/固定物品/刷怪笼/手办）",
                WorldExtract.expected(placed).containers(), WorldExtract.containers(ctx.level(), placed));
    }

    private static void entities(SceneContext ctx, StructureStage.Placed placed, Set<String> knownDead) {
        ServerLevel level = ctx.level();
        List<Entity> entities = WorldExtract.entities(level, placed);
        Checks.sameMultiset(ctx, "实体（类型/展示物/装备/画）", WorldExtract.expected(placed).entities(),
                entities.stream().map(e -> WorldExtract.entity(WorldExtract.nbtOf(e))).toList());
        Fidelity.entities(ctx, level, placed);
        Checks.after(ctx, SETTLE_TICKS, () -> {
            List<String> dead = new ArrayList<>();
            List<String> inWall = new ArrayList<>();
            for (Entity entity : entities) {
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                if (!(entity instanceof LivingEntity living) || knownDead.contains(type)) {
                    continue;
                }
                if (!living.isAlive() || living.isRemoved()) {
                    dead.add(type + "@" + entity.blockPosition());
                } else if (living.isInWall()) {
                    inWall.add(type + "@" + entity.blockPosition());
                }
            }
            ctx.check(dead).as(SETTLE_TICKS + " tick 后死亡或消失的生物").isEmpty();
            ctx.check(inWall).as(SETTLE_TICKS + " tick 后卡在方块里的生物").isEmpty();
        });
    }

    private static void maids(SceneContext ctx, StructureStage.Placed placed) {
        List<Map<String, String>> expected = new ArrayList<>(WorldExtract.expected(placed).maids());
        List<Map<String, String>> actual = new ArrayList<>(WorldExtract.entities(ctx.level(), placed).stream()
                .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(Actors.MAID))
                .map(e -> WorldExtract.maid(WorldExtract.nbtOf(e)))
                .toList());
        Comparator<Map<String, String>> order = Comparator.comparing(m -> m.get("model_id") + m.get("favorability"));
        expected.sort(order);
        actual.sort(order);
        ctx.check(actual.size()).as("结构女仆数量").isEqualTo(expected.size());
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            Map<String, String> want = expected.get(i);
            Map<String, String> got = actual.get(i);
            want.forEach((field, value) ->
                    ctx.check(got.get(field)).as("女仆[" + want.get("model_id") + "]." + field).isEqualTo(value));
        }
    }

    /** 已知缺陷：隐世樱树的狐狸按模板原始高度生成，没有跟随重力处理器落到结构地面。 */
    private static void foxOnGround(SceneContext ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, "touhou_little_maid_spell:hidden_cherry_tree");
        StructureStage.Piece piece = WorldExtract.ownPieces(placed).getFirst();
        int ground = piece.blocks().stream()
                .filter(info -> !info.state().isAir() && !info.state().is(Blocks.STRUCTURE_VOID))
                .mapToInt(info -> info.pos().getY()).min().orElseThrow();
        for (StructureTemplate.StructureEntityInfo fox : piece.entities()) {
            ctx.record("foxY", fox.pos.y);
            ctx.record("structureGroundY", ground);
            ctx.check(fox.pos.y).as("狐狸生成高度（结构最低方块 y=" + ground + "）").isBetween(ground, ground + 4);
        }
    }

    /** 街道的 minecraft:street 接口应指向本生物群系的原版街道池。 */
    private static void streetLinksOwnBiome(SceneContext ctx, String biome) {
        for (StructureTemplate.StructureBlockInfo jigsaw : jigsaws(ctx, VILLAGE + biome + "_street")) {
            if (jigsaw.nbt().getString("name").equals("minecraft:street")) {
                ctx.check(jigsaw.nbt().getString("pool")).as(biome + "_street 的街道接口 @" + jigsaw.pos())
                        .isEqualTo("minecraft:village/" + biome + "/streets");
            }
        }
    }

    private static List<StructureTemplate.StructureBlockInfo> jigsaws(SceneContext ctx, String template) {
        return ctx.level().getStructureManager().getOrCreate(ResourceLocation.parse(template))
                .filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW);
    }

    /** 服务器启动后五个原版村庄街道池里都注入了补充街道（权重 1）。 */
    private static void poolInjection(SceneContext ctx) {
        var pools = ctx.level().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        for (String biome : VILLAGE_BIOMES) {
            StructureTemplatePool pool = pools.get(ResourceLocation.parse("minecraft:village/" + biome + "/streets"));
            String street = VILLAGE + biome + "_street";
            List<Integer> weights = pool.rawTemplates.stream()
                    .filter(pair -> templateOf(pair.getFirst()).equals(street))
                    .map(Pair::getSecond).toList();
            ctx.check(weights).as("minecraft:village/" + biome + "/streets 中 " + street + " 的权重").isEqualTo(List.of(1));
            long entries = pool.templates.stream().filter(element -> templateOf(element).equals(street)).count();
            ctx.check(entries).as("minecraft:village/" + biome + "/streets 抽取列表中的条目数").isEqualTo(1L);
        }
    }

    private static String templateOf(StructurePoolElement element) {
        return element instanceof SinglePoolElement single ? Worldgen.templateId(single) : "";
    }

    /** 阴阳祭坛的缠魂水槽装着水（模板里的流体键 Goety 不读，逐键比对跳过，这里按能力检查语义）。 */
    private static void hauntedJugHasWater(SceneContext ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, "touhou_little_maid_spell:yin_yang_altar");
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureBlockInfo info : piece.blocks()) {
                if (!BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString().equals("goety:haunted_jug")) {
                    continue;
                }
                IFluidHandler handler = ctx.level().getCapability(Capabilities.FluidHandler.BLOCK, info.pos(), null);
                ctx.check(handler).as("缠魂水槽的流体能力 @" + info.pos()).isNotNull();
                if (handler != null) {
                    ctx.check(handler.getFluidInTank(0).getFluid().isSame(Fluids.WATER)).as("缠魂水槽装着水 @" + info.pos()).isTrue();
                }
            }
        }
    }

    /** 平原房屋炼药锅有 250mb 罕见墨水。 */
    private static void cauldronKeepsInk(SceneContext ctx) {
        for (CompoundTag saved : savedBlockEntities(ctx, placeHouse(ctx, "plains"), "irons_spellbooks:alchemist_cauldron")) {
            ListTag results = saved.getList("Results", Tag.TAG_COMPOUND);
            ctx.check(results.size()).as("炼药锅的液体条目数").isEqualTo(1);
            if (!results.isEmpty()) {
                ctx.check(results.getCompound(0).getString("id")).as("炼药锅液体").isEqualTo("irons_spellbooks:uncommon_ink");
                ctx.check(results.getCompound(0).getInt("amount")).as("炼药锅液体量").isEqualTo(250);
            }
        }
    }

    /** 泰加房屋展示台上的紫晶细剑保留名字、锋利 II 和召唤剑刃 2 级。 */
    private static void rapierKeepsData(SceneContext ctx) {
        for (CompoundTag saved : savedBlockEntities(ctx, placeHouse(ctx, "taiga"), "irons_spellbooks:pedestal")) {
            CompoundTag components = saved.getCompound("heldItem").getCompound("components");
            ctx.check(saved.getCompound("heldItem").getString("id")).as("展示台物品").isEqualTo("irons_spellbooks:amethyst_rapier");
            ctx.check(components.getString("minecraft:custom_name")).as("细剑名字").contains("远途之念");
            ctx.check(components.getCompound("minecraft:enchantments").getCompound("levels").getInt("minecraft:sharpness"))
                    .as("细剑锋利等级").isEqualTo(2);
            ListTag spells = components.getCompound("irons_spellbooks:spell_container").getList("data", Tag.TAG_COMPOUND);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < spells.size(); i++) {
                ids.add(spells.getCompound(i).getString("id") + "@" + spells.getCompound(i).getInt("level"));
            }
            ctx.check(ids).as("细剑法术").isEqualTo(List.of("irons_spellbooks:summon_swords@2"));
        }
    }

    /** 已知缺陷：平原房屋模板里的椅子不应嵌在实心方块里，也不应在同一位置重复。 */
    private static void chairsNotInBlocks(SceneContext ctx) {
        StructureStage.Placed placed = placeHouse(ctx, "plains");
        List<String> embedded = new ArrayList<>();
        List<String> positions = new ArrayList<>();
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureEntityInfo info : piece.entities()) {
                if (!info.nbt.getString("id").equals(CHAIR)) {
                    continue;
                }
                BlockPos pos = BlockPos.containing(info.pos);
                positions.add(info.pos.toString());
                if (ctx.level().getBlockState(pos).isCollisionShapeFullBlock(ctx.level(), pos)) {
                    embedded.add(pos + " " + BuiltInRegistries.BLOCK.getKey(ctx.level().getBlockState(pos).getBlock()));
                }
            }
        }
        ctx.check(embedded).as("嵌在实心方块里的椅子").isEmpty();
        ctx.check(positions.stream().distinct().count()).as("椅子位置去重后的数量（共 " + positions.size() + " 把）")
                .isEqualTo((long) positions.size());
    }

    /** 已知缺陷：模板里的方块都应来自已注册的方块（没有声明依赖的模组方块会变成空气）。 */
    private static void onlyRegisteredBlocks(SceneContext ctx) {
        List<String> missing = new ArrayList<>();
        StructureSnapshots.TEMPLATES.forEach((template, snapshot) -> snapshot.specialBlocks().stream()
                .filter(block -> !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(block)))
                .forEach(block -> missing.add(template + ": " + block)));
        ctx.check(missing).as("引用了未注册方块的模板").isEmpty();
    }
}
