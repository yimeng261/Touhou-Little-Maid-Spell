package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.github.yimeng261.maidspell.worldgen.StarfallGardenPlacementGuard;
import com.github.yimeng261.maidspell.worldgen.WorldgenTestAccess;
import com.github.yimeng261.maidspell.worldgen.structure.OptionalModTemplateStructure;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 可选模组模板结构的下界洞穴地面选址，以及星落之庭对其他结构落点的避让。
 * <p>选址用合成地形直接调用判定函数；避让既用真实主世界的结构集落点计划，也用合成的结构集与区块逐条核对规则。
 */
public final class OptionalWorldgenScenes {
    private static final String OVERWORLD = "minecraft:overworld";

    /** 地形柱的数组长度，覆盖 y=0~255。 */
    private static final int COLUMN_HEIGHT = 256;
    /** 下界顶层基岩的最低 y。 */
    private static final int NETHER_ROOF_Y = 123;
    private static final int MIN_FLOOR = 32;
    private static final int MAX_TOP = 122;

    private OptionalWorldgenScenes() {
    }

    public static List<Scene> dedicatedServer() {
        return List.of(
                Checks.scene("optionalTemplate.netherFloorSelection", 5, OptionalWorldgenScenes::netherFloorSelection),
                Checks.scene("starfallGarden.guardYieldsToOtherStructureStarts", 1200,
                        OptionalWorldgenScenes::guardYieldsToOtherStarts).withArena(false),
                Checks.scene("starfallGarden.guard.rejectsSameAndNeighbourChunks", 5, OptionalWorldgenScenes::guardNeighbours),
                Checks.scene("starfallGarden.guard.permitsTwoChunksAway", 5, OptionalWorldgenScenes::guardTwoAway),
                Checks.scene("starfallGarden.guard.ignoresOwnSetsButProtectsMixedSets", 5, OptionalWorldgenScenes::guardOwnSets),
                Checks.scene("starfallGarden.guard.independentOfSetOrder", 5, OptionalWorldgenScenes::guardSetOrder),
                Checks.scene("starfallGarden.guard.respectsFrequencyAndExclusion", 5, OptionalWorldgenScenes::guardFrequency),
                Checks.scene("starfallGarden.guard.ignoresSetsUnavailableInDimension", 5, OptionalWorldgenScenes::guardDimension),
                Checks.scene("starfallGarden.guard.savedValidStartsBlockRetrofit", 5, OptionalWorldgenScenes::savedStarts),
                Checks.scene("starfallGarden.guard.savedReferencesBlockRetrofit", 5, OptionalWorldgenScenes::savedReferences));
    }

    private static void netherFloorSelection(SceneContext ctx) {
        ctx.check(find(ctx, 11, 1, 3, column(42))).as("洞穴地面而不是基岩顶").isEqualTo(OptionalInt.of(42));

        BlockState[] lowCeiling = terrain(40);
        lowCeiling[64] = Blocks.NETHERRACK.defaultBlockState();
        Arrays.fill(lowCeiling, 72, NETHER_ROOF_Y, Blocks.NETHERRACK.defaultBlockState());
        ctx.check(find(ctx, 11, 1, 3, new NoiseColumn(0, lowCeiling))).as("低矮顶板之下找到更早的开阔洞穴")
                .isEqualTo(OptionalInt.of(40));

        BlockState[] lava = terrain(40);
        Arrays.fill(lava, 41, 61, Blocks.LAVA.defaultBlockState());
        ctx.check(find(ctx, 4, 1, 3, new NoiseColumn(0, lava))).as("岩浆覆盖的空间").isEqualTo(OptionalInt.empty());
        ctx.check(find(ctx, 4, 1, 3, column(20))).as("地面低于搜索下限").isEqualTo(OptionalInt.empty());

        ctx.check(find(ctx, 11, 1, 3, column(40), column(43), column(41))).as("小坡度可接受")
                .isEqualTo(OptionalInt.of(43));
        ctx.check(find(ctx, 11, 1, 3, column(40), column(44))).as("占地下的悬崖").isEqualTo(OptionalInt.empty());

        BlockState[] hole = terrain(39);
        hole[40] = Blocks.LAVA.defaultBlockState();
        ctx.check(find(ctx, 8, 0, 3, column(40), new NoiseColumn(0, hole))).as("采样点上有岩浆或缺少支撑")
                .isEqualTo(OptionalInt.empty());

        BlockState[] flooded = terrain(40);
        Arrays.fill(flooded, 41, 51, Blocks.WATER.defaultBlockState());
        ctx.check(find(ctx, 8, 1, 3, new NoiseColumn(0, flooded))).as("被水淹没的空间").isEqualTo(OptionalInt.empty());

        BlockState[] dripping = terrain(40);
        dripping[48] = Blocks.LAVA.defaultBlockState();
        ctx.check(find(ctx, 8, 0, 3, column(40), new NoiseColumn(0, dripping))).as("顶部垂下的岩浆")
                .isEqualTo(OptionalInt.empty());

        ctx.check(find(ctx, 11, 1, 3, column(110))).as("模板连同地面偏移刚好在顶层之下").isEqualTo(OptionalInt.of(110));
        ctx.check(find(ctx, 11, 1, 3, column(113))).as("地面偏移让模板顶到顶层").isEqualTo(OptionalInt.empty());
        ctx.check(find(ctx, 10, 0, 3, column(112))).as("无偏移时刚好容纳").isEqualTo(OptionalInt.of(112));
        ctx.check(find(ctx, 10, 1, 3, column(112))).as("有偏移时放不下").isEqualTo(OptionalInt.empty());
    }

    private static OptionalInt find(SceneContext ctx, int height, int offset, int maxStep, NoiseColumn... columns) {
        return Reflect.call(null, OptionalModTemplateStructure.class, "findNetherFloor",
                new Class<?>[]{NoiseColumn[].class, int.class, int.class, int.class, int.class, int.class},
                columns, MIN_FLOOR, MAX_TOP, height, offset, maxStep);
    }

    private static NoiseColumn column(int groundY) {
        return new NoiseColumn(0, terrain(groundY));
    }

    private static BlockState[] terrain(int groundY) {
        BlockState[] blocks = new BlockState[COLUMN_HEIGHT];
        Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
        Arrays.fill(blocks, 0, groundY + 1, Blocks.NETHERRACK.defaultBlockState());
        Arrays.fill(blocks, NETHER_ROOF_Y, 128, Blocks.BEDROCK.defaultBlockState());
        return blocks;
    }

    /**
     * 其他结构集的落点区块及相邻 8 个区块都会被庭院让出；整个范围里不是所有区块都被让出，
     * 说明避让只针对其他结构的落点，不是整片挡住。
     */
    private static void guardYieldsToOtherStarts(SceneContext ctx) {
        ServerLevel level = Worldgen.level(ctx, OVERWORLD);
        var state = level.getChunkSource().getGeneratorState();
        Structure garden = Worldgen.structure(level, NS + "starfall_garden").value();

        List<String> notYielded = new ArrayList<>();
        List<ChunkPos> candidates = Worldgen.candidates(level, Worldgen.structureSet(level, NS + "woods_perch").value(), 8);
        ctx.check(candidates).as("woods_perch 的落点候选").isNotEmpty();
        for (ChunkPos candidate : candidates.stream().limit(8).toList()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos near = new ChunkPos(candidate.x + dx, candidate.z + dz);
                    if (!StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state, garden, near)) {
                        notYielded.add(near.toString());
                    }
                }
            }
        }
        ctx.record("notYielded", notYielded);
        ctx.check(notYielded).as("其他结构落点周围一圈区块里庭院没有让出的").isEmpty();

        int free = 0;
        for (int x = -48; x <= 48 && free == 0; x++) {
            for (int z = -48; z <= 48; z++) {
                if (!StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state, garden, new ChunkPos(x, z))) {
                    free++;
                    break;
                }
            }
        }
        ctx.check(free).as("原点附近没有其他结构落点的区块数").isAtLeast(1);
    }

    // ---- 合成结构集上的避让规则 ----

    /** 避让检查只读结构集的落点计划，不会真的尝试生成别的结构。 */
    private static final class InertStructure extends Structure {
        private InertStructure(Holder<Biome> biome) {
            super(new StructureSettings(HolderSet.direct(biome), Map.of(),
                    GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        }

        @Override
        protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
            throw new AssertionError("避让检查不能尝试生成其他结构");
        }

        @Override
        public @NotNull StructureType<?> type() {
            throw new AssertionError("避让检查只需要结构集的 placement");
        }
    }

    private static Holder<Biome> newBiome() {
        return Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(true).temperature(0.8F).downfall(0.4F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build());
    }

    /** 同一合成群系里的庭院与另一个结构。 */
    private record Fixture(Holder<Biome> biome, Structure garden, Structure other) {
        private static Fixture create() {
            Holder<Biome> biome = newBiome();
            return new Fixture(biome, new InertStructure(biome), new InertStructure(biome));
        }

        @SafeVarargs
        private ChunkGeneratorStructureState state(Holder<StructureSet>... sets) {
            return ChunkGeneratorStructureState.createForFlat(null, 42L, new FixedBiomeSource(biome), Arrays.stream(sets));
        }

        private boolean attempt(ChunkGeneratorStructureState state, ChunkPos pos) {
            return StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state, garden, pos);
        }
    }

    private static Holder<StructureSet> set(StructurePlacement placement, Structure... structures) {
        return Holder.direct(new StructureSet(Arrays.stream(structures)
                .map(structure -> new StructureSet.StructureSelectionEntry(Holder.direct(structure), 1)).toList(), placement));
    }

    /** separation = spacing - 1，落点固定在 64 的倍数，正好覆盖区块边界和负坐标。 */
    private static RandomSpreadStructurePlacement gridPlacement() {
        return new RandomSpreadStructurePlacement(64, 63, RandomSpreadType.LINEAR, 123);
    }

    private static RandomSpreadStructurePlacement everyChunkPlacement() {
        return new RandomSpreadStructurePlacement(1, 0, RandomSpreadType.LINEAR, 123);
    }

    /** 落点区块本身和相邻 8 个区块都让出，且不会触发其他结构的生成。 */
    private static void guardNeighbours(SceneContext ctx) {
        Fixture f = Fixture.create();
        var state = f.state(set(gridPlacement(), f.other()));
        List<String> notYielded = new ArrayList<>();
        for (int anchorX : new int[]{-64, 0, 64}) {
            for (int anchorZ : new int[]{-64, 0, 64}) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        ChunkPos pos = new ChunkPos(anchorX + dx, anchorZ + dz);
                        if (!f.attempt(state, pos)) {
                            notYielded.add(pos.toString());
                        }
                    }
                }
            }
        }
        ctx.check(notYielded).as("落点周围没让出的区块").isEmpty();
    }

    /** 相隔两个区块及以上（含负坐标）的候选不受影响。 */
    private static void guardTwoAway(SceneContext ctx) {
        Fixture f = Fixture.create();
        var state = f.state(set(gridPlacement(), f.other()));
        for (ChunkPos candidate : List.of(new ChunkPos(2, 0), new ChunkPos(-2, 0),
                new ChunkPos(0, 2), new ChunkPos(0, -2), new ChunkPos(-62, 66))) {
            ctx.check(f.attempt(state, candidate)).as(candidate + " 被让出").isFalse();
        }
    }

    /** 只含庭院自己的结构集不算；混合结构集里有别的结构时照样避让。 */
    private static void guardOwnSets(SceneContext ctx) {
        Fixture f = Fixture.create();
        var ownSet = set(everyChunkPlacement(), f.garden());
        ctx.check(f.attempt(f.state(ownSet), new ChunkPos(0, 0))).as("只有庭院自己的结构集").isFalse();
        ctx.check(f.attempt(f.state(ownSet, set(everyChunkPlacement(), f.garden(), f.other())), new ChunkPos(0, 0)))
                .as("混合结构集").isTrue();
    }

    /** 结果与结构集的先后顺序无关。 */
    private static void guardSetOrder(SceneContext ctx) {
        Fixture f = Fixture.create();
        var ownSet = set(everyChunkPlacement(), f.garden());
        var otherSet = set(gridPlacement(), f.other());
        ctx.check(f.attempt(f.state(ownSet, otherSet), new ChunkPos(1, -1))).as("自己的在前").isTrue();
        ctx.check(f.attempt(f.state(otherSet, ownSet), new ChunkPos(1, -1))).as("别人的在前").isTrue();
    }

    /** 频率为 0 的结构集、和庭院互斥的结构集都不会让庭院避让。 */
    private static void guardFrequency(SceneContext ctx) {
        Fixture f = Fixture.create();
        var disabled = new RandomSpreadStructurePlacement(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT,
                0.0F, 123, Optional.empty(), 1, 0, RandomSpreadType.LINEAR);
        ctx.check(f.attempt(f.state(set(disabled, f.other())), new ChunkPos(0, 0))).as("频率为 0").isFalse();
        var ownSet = set(everyChunkPlacement(), f.garden());
        var excluded = new RandomSpreadStructurePlacement(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT,
                1.0F, 123, Optional.of(new StructurePlacement.ExclusionZone(ownSet, 1)), 1, 0, RandomSpreadType.LINEAR);
        ctx.check(f.attempt(f.state(ownSet, set(excluded, f.other())), new ChunkPos(0, 0))).as("与庭院互斥").isFalse();
    }

    /** 当前维度群系里不会出现的结构所在的结构集不参与避让。 */
    private static void guardDimension(SceneContext ctx) {
        Fixture f = Fixture.create();
        Structure unavailable = new InertStructure(newBiome());
        ctx.check(f.attempt(f.state(set(everyChunkPlacement(), unavailable)), new ChunkPos(0, 0)))
                .as("别的群系才有的结构").isFalse();
    }

    private static ProtoChunk chunk(SceneContext ctx) {
        return new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384),
                ctx.server().registryAccess().registryOrThrow(Registries.BIOME), null);
    }

    private static StructureStart start(Structure structure) {
        return new StructureStart(structure, new ChunkPos(0, 0), 0, new PiecesContainer(
                List.of(new BuriedTreasurePieces.BuriedTreasurePiece(new BlockPos(0, 64, 0)))));
    }

    /** 已存的有效起点挡住补生成；失败的起点和庭院自己的起点不挡。 */
    private static void savedStarts(SceneContext ctx) {
        Fixture f = Fixture.create();
        ProtoChunk chunk = chunk(ctx);
        chunk.setStartForStructure(f.garden(), start(f.garden()));
        chunk.setStartForStructure(f.other(), StructureStart.INVALID_START);
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden())).as("只有庭院起点和失败起点").isFalse();
        chunk.setStartForStructure(f.other(), start(f.other()));
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden())).as("有别的有效起点").isTrue();
    }

    /** 起点在远处区块的结构，只要本区块存了引用就挡住补生成；查询过但没有引用的不算。 */
    private static void savedReferences(SceneContext ctx) {
        Fixture f = Fixture.create();
        ProtoChunk chunk = chunk(ctx);
        chunk.addReferenceForStructure(f.garden(), ChunkPos.asLong(0, 0));
        // 原版查询一个没有引用的结构会返回空集合，不能把它当成占用
        chunk.getReferencesForStructure(f.other());
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden())).as("只有庭院引用").isFalse();
        chunk.addReferenceForStructure(f.other(), ChunkPos.asLong(5, -5));
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden())).as("有别的结构引用").isTrue();
    }
}
