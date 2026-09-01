package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * 观星塔生成于末地外岛，按塔基占地计算落点高度。
 * 地形适配按每个拼图件处理，塔身较宽的部分可能在空中生成末地石边缘。
 */
public class StarwatchTowerStructure extends Structure {
    public static final MapCodec<StarwatchTowerStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
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
     * 拼图把起始件的底层放在 {@code startPos.getY() - groundLevelDelta} 上，见 {@code JigsawPlacement.addPieces} 里
     * {@code piece.move(0, startY - (bbox.minY() + groundLevelDelta), 0)}；单件池元素的 {@code getGroundLevelDelta()} 恒为 1，
     * 所以传进去的 Y 要先加回这 1，底层才正好落在算出来的那一格。
     */
    private static final int GROUND_LEVEL_DELTA = 1;

    /**
     * 模板底下垫的地基层数。25x21 的平底建筑摆在起伏地形上，总有几列的地面比落点低一格，露出来就是塔底一圈缝。
     * {@code starwatch_tower_1.nbt} 最底下那层末地石由 {@code tools/extend_structure_base.py} 垫在轮廓内，专门填这种一格的缝：
     * 贴合处顶掉的本来就是末地石，悬空处把缝补上；落差两格以上的断崖它管不了。层数要从落点里减掉，否则整座塔会跟着抬高一格。
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

    /** 在塔基占地内取地表第一格空气的中位高度，避开岛缘与凸起的离群值。 */
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
                this.maxDistanceFromCenter,
                PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO,
                LiquidSettings.APPLY_WATERLOGGING
        );
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.STARWATCH_TOWER.get();
    }
}
