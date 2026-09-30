package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.data.StructureSnapshots;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Fidelity;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.StructureStage;
import com.github.yimeng261.maidspell.stagewright.support.WorldExtract;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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

    /** 挂着画的模板（本模组命名空间下的路径）、放置时注册的结构、强制加载区块半径。 */
    private record PaintingTemplate(String path, String structure, int radius) {
    }

    /** 本模组所有挂着画的模板，由 structure.paintings.templatesListed 与模板池核对。 */
    private static final List<PaintingTemplate> PAINTING_TEMPLATES = List.of(
            new PaintingTemplate("starwatch_tower/starwatch_tower_1", Checks.NS + "starwatch_tower", 2),
            new PaintingTemplate("starwatch_tower/starwatch_tower_2", Checks.NS + "starwatch_tower", 3),
            new PaintingTemplate("starfall_garden_5", Checks.NS + "starfall_garden", 3),
            new PaintingTemplate("hidden_retreat/hidden_retreat_building", Checks.NS + "hidden_retreat", 2),
            new PaintingTemplate("fairy_maid_cafe/fairy_maid_cafe", Checks.NS + "fairy_maid_cafe", 2),
            new PaintingTemplate("enchantress_footsteps/village/snowy_house", "minecraft:village_snowy", 2));

    /**
     * 画的中心在偶数尺寸方向上落在方块边界，按中心取整时两组旋转的边界取向相反：
     * 原 x 在 {NONE, CW90} 与 {CW180, CCW90} 间翻转，原 z 在 {NONE, CCW90} 与 {CW90, CW180} 间翻转，
     * 一对相反旋转就覆盖了两个轴的两种取向。
     */
    private static final List<Rotation> PAINTING_ROTATIONS = List.of(Rotation.NONE, Rotation.CLOCKWISE_180);

    private static final String PAINTING = "minecraft:painting";

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
        scenes.add(Checks.scene("structure.paintings.anchorMechanism", 20, StructureScenes::paintingAnchorMechanism));
        scenes.add(Checks.scene("structure.paintings.templatesListed", 20, StructureScenes::paintingTemplatesListed));
        for (PaintingTemplate painting : PAINTING_TEMPLATES) {
            for (Rotation rotation : PAINTING_ROTATIONS) {
                scenes.add(structureScene("structure.paintings." + painting.path().replace('/', '.') + "."
                        + rotation.name().toLowerCase(Locale.ROOT), painting.radius(), ctx -> paintingsStayAnchored(ctx, painting, rotation)));
            }
        }
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

    /**
     * 按指定旋转单独放置一个模板（取模板池里的拼图元素，处理器与自然生成一致），逐幅比对变体、挂靠格和朝向，
     * 并当场判定每幅画能否挂住（原版每 100 tick 才做一次同样的判定）。
     * 预期挂靠格是模板 blockPos 经原版变换后的位置，偏一格也算错。
     */
    private static void paintingsStayAnchored(SceneContext ctx, PaintingTemplate painting, Rotation rotation) {
        ServerLevel level = ctx.level();
        StructureStage.Placed placed = StructureStage.placeElement(ctx, painting.structure(),
                Worldgen.poolElement(level, Checks.NS + painting.path()), rotation);
        StructureStage.Piece piece = placed.pieces().getFirst();
        List<String> expected = piece.entities().stream()
                .filter(StructureScenes::isPainting)
                .map(info -> paintingKey(info.nbt.getString("variant"), info.blockPos,
                        rotation.rotate(Direction.from2DDataValue(info.nbt.getByte("facing")))))
                .toList();
        ctx.check(expected).as(piece.template() + " 里的画").isNotEmpty();

        List<Painting> paintings = level.getEntitiesOfClass(Painting.class, AABB.of(piece.box()).inflate(2), Entity::isAlive);
        List<String> actual = paintings.stream()
                .map(p -> paintingKey(p.getVariant().unwrapKey().map(key -> key.location().toString()).orElse("?"),
                        p.getPos(), p.getDirection()))
                .toList();
        ctx.record("paintings", actual);
        Checks.sameMultiset(ctx, "放置后的画（变体 @挂靠格 朝向）", expected, actual);
        ctx.check(paintings.stream().filter(p -> !p.survives()).map(p -> p.getPos().toShortString()).toList())
                .as("挂不住的画").isEmpty();
    }

    private static boolean isPainting(StructureTemplate.StructureEntityInfo info) {
        return info.nbt.getString("id").equals(PAINTING);
    }

    private static String paintingKey(String variant, BlockPos anchor, Direction facing) {
        return variant + " @" + anchor.toShortString() + " " + facing;
    }

    /**
     * 放置模板时画的挂靠格等于模板 blockPos 经旋转变换后的位置：每种尺寸的画按四个朝向保存成模板实体信息
     * （位置取实体中心，与结构方块保存的一致），四种旋转下经 processEntityInfos 处理后按位置取整都应回到 blockPos。
     * 同时确认用例里既有偶数高度、也有偶数宽度的画在直接按中心取整时会错位，保证覆盖了两类情形。
     */
    private static void paintingAnchorMechanism(SceneContext ctx) {
        ServerLevel level = ctx.level();
        Map<String, Holder<PaintingVariant>> bySize = new TreeMap<>();
        level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).holders()
                .forEach(variant -> bySize.putIfAbsent(variant.value().width() + "x" + variant.value().height(), variant));
        BlockPos anchor = new BlockPos(8, 8, 8);
        List<StructureTemplate.StructureEntityInfo> raw = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        bySize.forEach((size, variant) -> {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                Painting painting = new Painting(level, anchor, facing, variant);
                CompoundTag nbt = new CompoundTag();
                painting.save(nbt);
                raw.add(new StructureTemplate.StructureEntityInfo(painting.position(), anchor, nbt));
                labels.add(size + " " + facing);
            }
        });
        ctx.record("cases", raw.size() * Rotation.values().length);

        List<String> misplaced = new ArrayList<>();
        int heightMisses = 0;
        int widthMisses = 0;
        for (Rotation rotation : Rotation.values()) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
            List<StructureTemplate.StructureEntityInfo> placed =
                    StructureTemplate.processEntityInfos(null, level, BlockPos.ZERO, settings, raw);
            for (int i = 0; i < raw.size(); i++) {
                BlockPos expected = placed.get(i).blockPos;
                BlockPos rounded = BlockPos.containing(StructureTemplate.transformedVec3d(settings, raw.get(i).pos));
                heightMisses += rounded.getY() != expected.getY() ? 1 : 0;
                widthMisses += rounded.getX() != expected.getX() || rounded.getZ() != expected.getZ() ? 1 : 0;
                BlockPos actual = BlockPos.containing(placed.get(i).pos);
                if (!actual.equals(expected)) {
                    misplaced.add(labels.get(i) + " " + rotation + ": " + actual.toShortString() + " ≠ " + expected.toShortString());
                }
            }
        }
        ctx.record("heightMisses", heightMisses);
        ctx.record("widthMisses", widthMisses);
        ctx.check(heightMisses).as("按中心取整会上下错位的用例数（偶数高度）").isGreaterThan(0);
        ctx.check(widthMisses).as("按中心取整会水平错位的用例数（偶数宽度）").isGreaterThan(0);
        ctx.check(misplaced).as("处理后挂靠格偏离 blockPos 的画").isEmpty();
    }

    /** 本模组模板池引用的模板里，挂着画的正好是 PAINTING_TEMPLATES 列出的那些。 */
    private static void paintingTemplatesListed(SceneContext ctx) {
        StructureTemplateManager manager = ctx.level().getStructureManager();
        List<String> withPaintings = new ArrayList<>();
        Worldgen.poolElements(ctx.level()).keySet().stream().filter(id -> id.startsWith(Checks.NS)).forEach(id -> {
            List<StructureTemplate.StructureEntityInfo> entities =
                    Reflect.field(manager.getOrCreate(ResourceLocation.parse(id)), StructureTemplate.class, "entityInfoList");
            if (entities.stream().anyMatch(StructureScenes::isPainting)) {
                withPaintings.add(id);
            }
        });
        Checks.sameSet(ctx, "挂着画的模板（PAINTING_TEMPLATES）",
                PAINTING_TEMPLATES.stream().map(painting -> Checks.NS + painting.path()).toList(), withPaintings);
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
            ctx.check(components.getString("minecraft:custom_name")).as("细剑名字").contains("item.touhou_little_maid_spell.loot.wanderlust_rapier");
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
        for (String template : StructureSnapshots.TEMPLATES.keySet()) {
            ResourceLocation id = ResourceLocation.parse(template);
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
            try (var stream = ctx.server().getResourceManager().getResourceOrThrow(file).open()) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                List<ListTag> palettes = new ArrayList<>();
                palettes.add(nbt.getList("palette", Tag.TAG_COMPOUND));
                for (Tag alternative : nbt.getList("palettes", Tag.TAG_LIST)) palettes.add((ListTag) alternative);
                for (ListTag palette : palettes) {
                    for (Tag entry : palette) {
                        String block = ((CompoundTag) entry).getString("Name");
                        if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(block))) {
                            missing.add(template + ": " + block);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        ctx.check(missing).as("引用了未注册方块的模板").isEmpty();
    }
}
