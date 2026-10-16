package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.github.yimeng261.maidspell.worldgen.StarfallGardenPlacementGuard;
import com.github.yimeng261.maidspell.worldgen.structure.OptionalModTemplateStructure;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 可选模组模板结构的下界洞穴地面选址，以及星落之庭对其他结构落点的避让。
 * <p>选址用合成地形直接调用判定函数；避让用真实主世界的结构集落点计划。
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
                        OptionalWorldgenScenes::guardYieldsToOtherStarts).withArena(false));
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
}
