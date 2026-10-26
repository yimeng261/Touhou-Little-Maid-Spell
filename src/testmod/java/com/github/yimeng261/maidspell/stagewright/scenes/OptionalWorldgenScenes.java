package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.github.yimeng261.maidspell.worldgen.StarfallGardenData;
import com.github.yimeng261.maidspell.worldgen.StarfallGardenRetrofit;
import com.github.yimeng261.maidspell.worldgen.WorldgenTestAccess;
import com.github.yimeng261.maidspell.worldgen.structure.OptionalModTemplateStructure;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 可选模组模板结构的下界洞穴地面选址，以及星落之庭补生成对已存结构的避让。
 * <p>选址用合成地形直接调用判定函数；避让用合成的区块和结构起点逐条核对规则。
 */
public final class OptionalWorldgenScenes {
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
                Checks.scene("starfallGarden.guard.savedStartsBlockOnlyWhenVerticallyOverlapping", 5,
                        OptionalWorldgenScenes::savedStartsOverlap),
                Checks.scene("starfallGarden.guard.invalidStartsAndGardenItselfDoNotBlock", 5,
                        OptionalWorldgenScenes::savedStarts),
                Checks.scene("starfallGarden.guard.savedReferencesAloneDoNotBlock", 5,
                        OptionalWorldgenScenes::savedReferences),
                Checks.scene("starfallGarden.switchOffClosesGates", 20, OptionalWorldgenScenes::switchOff));
    }

    /**
     * 总开关关闭时三道闸门都不放行：找不到生成点、手动 generate（/place structure 走的路径）得到无效起点、
     * 旧存档补生成直接返回且不写「已尝试」标记；场景结束后还原开关。
     */
    private static void switchOff(SceneContext ctx) {
        boolean previous = Config.enableStarfallGarden;
        Config.enableStarfallGarden = false;
        ctx.cleanup(() -> Config.enableStarfallGarden = previous);
        ServerLevel level = ctx.server().overworld();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "starfall_garden"));
        ctx.check(structure != null).as("星落之庭结构已注册").isTrue();
        if (structure == null) {
            return;
        }
        ChunkPos chunk = new ChunkPos(level.getSharedSpawnPos());
        Structure.GenerationContext generation = Worldgen.context(level, chunk, holder -> true);
        ctx.check(structure.findValidGenerationPoint(generation).isPresent()).as("关闭时能找到生成点").isFalse();
        StructureStart start = structure.generate(generation.registryAccess(), generation.chunkGenerator(), generation.biomeSource(),
                generation.randomState(), generation.structureTemplateManager(), generation.seed(), chunk, 0,
                generation.heightAccessor(), generation.validBiome());
        ctx.check(start.isValid()).as("关闭时手动 generate 得到有效起点").isFalse();

        boolean attemptedBefore = StarfallGardenData.isRetrofitAttempted(ctx.server());
        Reflect.call(null, StarfallGardenRetrofit.class, "runRetrofit", new Class<?>[]{MinecraftServer.class}, ctx.server());
        ctx.check(StarfallGardenData.isRetrofitAttempted(ctx.server())).as("关闭时补生成不写已尝试标记").isEqualTo(attemptedBefore);
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

    // ---- 补生成对已存结构的避让规则 ----

    /** 避让检查只读已保存的结构数据，不会真的尝试生成结构。 */
    private static final class InertStructure extends Structure {
        private InertStructure(Holder<Biome> biome) {
            super(new StructureSettings(HolderSet.direct(biome), Map.of(),
                    GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        }

        @Override
        protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
            throw new AssertionError("避让检查不能尝试生成结构");
        }

        @Override
        public @NotNull StructureType<?> type() {
            throw new AssertionError("避让检查只需要已保存的结构数据");
        }
    }

    private static Holder<Biome> newBiome() {
        return Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(true).temperature(0.8F).downfall(0.4F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build());
    }

    /** 同一合成群系里的庭院与另一个结构。 */
    private record Fixture(Structure garden, Structure other) {
        private static Fixture create() {
            Holder<Biome> biome = newBiome();
            return new Fixture(new InertStructure(biome), new InertStructure(biome));
        }
    }

    private static ProtoChunk chunk(SceneContext ctx) {
        return new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384),
                ctx.server().registryAccess().registryOrThrow(Registries.BIOME), null);
    }

    private static StructureStart start(Structure structure) {
        return new StructureStart(structure, new ChunkPos(0, 0), 0, new PiecesContainer(
                List.of(new BuriedTreasurePieces.BuriedTreasurePiece(new BlockPos(0, 64, 0)))));
    }

    /** 已存的有效起点只有和庭院包围盒在竖直方向重叠时才挡补生成。 */
    private static void savedStartsOverlap(SceneContext ctx) {
        Fixture f = Fixture.create();
        ProtoChunk chunk = chunk(ctx);
        StructureStart otherStart = start(f.other());
        chunk.setStartForStructure(f.other(), otherStart);
        BoundingBox otherBox = otherStart.getBoundingBox();

        // 庭院完全在对方上方：地下、地面结构不拦补生成
        BoundingBox above = new BoundingBox(otherBox.minX(), otherBox.maxY() + 20, otherBox.minZ(),
                otherBox.maxX(), otherBox.maxY() + 60, otherBox.maxZ());
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden(), above)).as("庭院在别的结构上方").isFalse();

        // 竖直范围重叠：同高度或伸进庭院高度带的结构仍然要挡
        BoundingBox overlapping = new BoundingBox(otherBox.minX(), otherBox.minY(), otherBox.minZ(),
                otherBox.maxX(), otherBox.maxY(), otherBox.maxZ());
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden(), overlapping)).as("和别的结构竖直重叠").isTrue();
    }

    /** 失败的起点和庭院自己的起点不挡补生成。 */
    private static void savedStarts(SceneContext ctx) {
        Fixture f = Fixture.create();
        ProtoChunk chunk = chunk(ctx);
        chunk.setStartForStructure(f.garden(), start(f.garden()));
        chunk.setStartForStructure(f.other(), StructureStart.INVALID_START);
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden(), start(f.garden()).getBoundingBox()))
                .as("只有庭院起点和失败起点").isFalse();
    }

    /** 只存了引用的结构不挡补生成：引用所在区块不一定是起点，拿不到可靠高度。 */
    private static void savedReferences(SceneContext ctx) {
        Fixture f = Fixture.create();
        ProtoChunk chunk = chunk(ctx);
        chunk.addReferenceForStructure(f.other(), ChunkPos.asLong(5, -5));
        ctx.check(WorldgenTestAccess.hasOtherStructure(chunk, f.garden(), start(f.garden()).getBoundingBox()))
                .as("只有别的结构引用").isFalse();
    }
}
