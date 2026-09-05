package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * 观星塔结构。
 *
 * <p>生成条件与末地城一致（外岛地表、地表低于 {@code min_surface_y} 视为虚空或岛缘就放弃），
 * 但<b>落点算法不同</b>。末地城那套 {@code getLowestYIn5by5BoxOffset7Blocks} 在这里会把塔埋进地里，
 * 原因有两条，都在 {@link #GROUND_LEVEL_DELTA} 和 {@link #representativeSurfaceInFootprint} 上写着：
 * 它返回的是最顶上那格实心方块（比塔基该在的位置低 1），而拼图又会再把底层往下挪 1；
 * 加上它只在一个偏移 7 格的 5x5 框上取 4 个角的<b>最低</b>值，和这座塔 25x21 的占地对不上。
 *
 * <p>数据包里开了 {@code terrain_adaptation: beard_thin}：地形会顺着塔基垫上来，探进塔里的
 * 岩石也会被削掉，落点算不准的那一格由它兜底；模板底下另有一层写死的地基，见
 * {@link #BASE_SKIRT_LAYERS}。
 *
 * <p><b>它有个躲不掉的副作用</b>，记在这里省得下次再查一遍：胡须是按<b>每个拼图件的盒子</b>
 * 各算一遍的，而 {@code Beardifier} 在盒底往下两格加的密度（+0.557、+0.492）压得过末地
 * {@code final_density} 被 {@code squeeze} 钳住的下限 −0.458——盒子正下方那两格必定变实心。
 * 塔身第二件悬在地面上方 24 格、占地 43x24，比第一件的 25x21 宽出一圈，于是那一圈
 * （约 507 列）会在半空多出一层末地石。原版末地城同样是垂直堆叠的拼图塔，
 * 它的 {@code terrain_adaptation} 就是 {@code none}，多半正是为了躲开这件事。
 * 真要既贴地又不留浮块，得让塔身各件不参与胡须（Forge 的 {@code PieceBeardifierModifier}），
 * 或者把三件合成一件。
 *
 * <p>与 {@link StellarEndshoreStructure} 的区别就在这里：那一座是悬在半空的固定高度，
 * 这一座是踩在岛上的塔。
 */
public class StarwatchTowerStructure extends Structure {
    public static final Codec<StarwatchTowerStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size),
                    Codec.intRange(1, 256)
                            .optionalFieldOf("max_distance_from_center", 256)
                            .forGetter(structure -> structure.maxDistanceFromCenter),
                    Codec.intRange(0, 320)
                            .optionalFieldOf("min_surface_y", 60)
                            .forGetter(structure -> structure.minSurfaceY),
                    // 微调用：塔基相对地表再上下挪几格，改数据包就能试，不用重新编译。
                    Codec.intRange(-16, 16)
                            .optionalFieldOf("vertical_offset", 0)
                            .forGetter(structure -> structure.verticalOffset)
            ).apply(instance, StarwatchTowerStructure::new)
    );

    /**
     * 拼图把起始件的底层放在 {@code startPos.getY() - groundLevelDelta} 上，
     * 见 {@code JigsawPlacement.addPieces} 里那句
     * {@code piece.move(0, startY - (bbox.minY() + groundLevelDelta), 0)}。
     * 单件池元素的 {@code getGroundLevelDelta()} 恒为 1，所以传进去的 Y 要先加回这 1，
     * 底层才会正好落在我们算出来的那一格上。
     */
    private static final int GROUND_LEVEL_DELTA = 1;

    /**
     * 模板底下垫的地基层数。
     *
     * <p>25x21 的平底建筑摆在起伏地形上，总有几列的地面比落点低一格，露出来就是塔底一圈缝。
     * {@code starwatch_tower_1.nbt} 最底下那层末地石是 {@code tools/extend_structure_base.py}
     * 垫的，只铺在塔的轮廓内，专门填这种一格的缝：贴合的地方它顶掉的本来就是末地石，
     * 看不出区别；悬空的地方它把缝补上。落差两格以上的断崖它管不了，那已经不是「一格」的事。
     *
     * <p>层数要从落点里减掉，塔身才留在原来那个高度上——不减的话整座塔会跟着抬高一格。
     */
    private static final int BASE_SKIRT_LAYERS = 1;

    /**
     * 取样半径。起始件占地 25x21，拼图还会随机转朝向，所以按最长边往外罩一圈。
     */
    private static final int FOOTPRINT_RADIUS = 13;

    /**
     * 取样步长。半径 13 配步长 6 是 25 个点，够描出塔基那块地的起伏，又不至于每个候选区块
     * 都去算上千次高度图。
     */
    private static final int SAMPLE_STEP = 6;

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;
    private final int minSurfaceY;
    private final int verticalOffset;

    public StarwatchTowerStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
                                   int maxDistanceFromCenter, int minSurfaceY, int verticalOffset) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.minSurfaceY = minSurfaceY;
        this.verticalOffset = verticalOffset;
    }

    /**
     * 塔基那块地的代表高度，取<b>中位数</b>。
     *
     * <p>{@code getFirstFreeHeight} 给的是地表<b>之上</b>第一格空气的 Y，也就是塔基该在的那一格；
     * 末地城用的 {@code getLowestYIn5by5BoxOffset7Blocks} 走的是 {@code getFirstOccupiedHeight}，
     * 那是最顶上那格<b>实心方块</b>的 Y，比这里低 1。那个方法在原版里已经标了 {@code @Deprecated}，
     * 取样框还固定偏 7 格、只看 4 个角，和这座塔 25x21 的占地对不上，所以这里自己采。
     *
     * <p>为什么是中位数：25x21 的平底建筑摆在噪声地形上，<b>不存在</b>一个让整个底面都贴合的高度，
     * 只能选偏差最小的那个。取最低会被岛缘那种断崖式的采样点拽下去（塔埋进山里），
     * 取最高会被一处凸起顶上来（塔整体悬空一格，就是上一版的毛病）。中位数对两头的离群点都不敏感，
     * 落在占地面积里最普遍的那个地面高度上。
     *
     * <p>剩下的一格误差是平底建筑加起伏地形的固有问题，交给 {@code vertical_offset} 手调：
     * 想让它宁可埋一点也别悬空，填 -1。
     */
    private static int representativeSurfaceInFootprint(GenerationContext context, int centerX, int centerZ) {
        int span = FOOTPRINT_RADIUS * 2 / SAMPLE_STEP + 1;
        int[] samples = new int[span * span];
        int n = 0;
        for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz += SAMPLE_STEP) {
                samples[n++] = context.chunkGenerator().getFirstFreeHeight(
                        centerX + dx, centerZ + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(),
                        context.randomState()
                );
            }
        }
        Arrays.sort(samples);
        return samples[n / 2];
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        int centerX = context.chunkPos().getMiddleBlockX();
        int centerZ = context.chunkPos().getMiddleBlockZ();

        int surface = representativeSurfaceInFootprint(context, centerX, centerZ);
        // surface - 1 是地基层，也就是整座塔最低的那一格。低于这条线的地方要么是虚空，要么是岛缘。
        if (surface - 1 < this.minSurfaceY) {
            return Optional.empty();
        }

        // 地基层落在 surface - 1（最顶上那格实心方块）上，塔身底层还是 surface，和没垫地基时一样。
        BlockPos startPos = new BlockPos(centerX,
                surface + GROUND_LEVEL_DELTA - BASE_SKIRT_LAYERS + this.verticalOffset, centerZ);
        return JigsawPlacement.addPieces(
                context,
                this.startPool,
                Optional.empty(),
                this.size,
                startPos,
                false,
                Optional.empty(),
                this.maxDistanceFromCenter
        );
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.STARWATCH_TOWER.get();
    }
}
