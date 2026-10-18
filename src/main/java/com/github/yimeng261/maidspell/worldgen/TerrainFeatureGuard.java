package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 玄武岩三角洲的玄武岩石柱林、灵魂沙峡谷的玄武岩柱、三角洲和涌泉不长进 {@link #TAG} 里结构的占地：
 * 底框水平外扩 {@link #MARGIN} 格、从底框最低处往下一层（地板下面的地面）往上的范围内不写这些地物的方块。
 * 这几样都是地表结构放完之后才生成的地物，不拦的话玄武岩石柱林和玄武岩柱会把结构包住或贴着墙长、埋住门，
 * 三角洲和涌泉会把露天的地板、屋顶和厚墙换成熔岩。只拦落进这个范围的方块，范围外照常生成。
 */
public final class TerrainFeatureGuard {
    public static final TagKey<Structure> TAG = TagKey.create(Registries.STRUCTURE,
            new ResourceLocation(MaidSpellMod.MOD_ID, "no_terrain_features"));
    private static final int MARGIN = 2;
    /**
     * 一次地物放置离起点最远的水平距离：玄武岩石柱林散布 8 格加单根柱子的半径 3 格，三角洲半径 7 格加内圈偏移 2 格，
     * 灵魂沙峡谷的柱子底座半径 3 格。
     */
    private static final int REACH = 11;
    /** 结构起点区块离结构底框的最远距离（区块数），超出的结构起点不用看。 */
    private static final int START_CHUNK_SLACK = 1;

    private static final ThreadLocal<List<BoundingBox>> PROTECTED = ThreadLocal.withInitial(List::of);

    private TerrainFeatureGuard() {
    }

    /** 一次要写很多格的地物（玄武岩石柱林、玄武岩柱、三角洲）开始放之前，找出它够得着的受保护结构。 */
    public static void begin(WorldGenLevel level, BlockPos origin) {
        PROTECTED.set(find(level, origin, REACH + MARGIN));
    }

    public static void end() {
        PROTECTED.remove();
    }

    /** pos 是否落在 {@link #begin} 找出的受保护结构范围里。 */
    public static boolean blocked(BlockPos pos) {
        return blocked(PROTECTED.get(), pos);
    }

    /** 只写一格的地物（涌泉）用：现查 pos 附近的受保护结构。 */
    public static boolean blocked(WorldGenLevel level, BlockPos pos) {
        return blocked(find(level, pos, MARGIN), pos);
    }

    private static boolean blocked(List<BoundingBox> boxes, BlockPos pos) {
        for (BoundingBox box : boxes) {
            if (pos.getY() >= box.minY() - 1
                    && pos.getX() >= box.minX() - MARGIN && pos.getX() <= box.maxX() + MARGIN
                    && pos.getZ() >= box.minZ() - MARGIN && pos.getZ() <= box.maxZ() + MARGIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * 起点附近区块里的受保护结构起点。地物阶段只能读到生成中心 8 个区块以内的结构起点，
     * 地物起点在中心区块内，要看的范围远小于这个距离。
     */
    private static List<BoundingBox> find(WorldGenLevel level, BlockPos origin, int range) {
        if (!(level instanceof WorldGenRegion region)) {
            return List.of();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkPos center = region.getCenter();
        int minX = ((origin.getX() - range) >> 4) - START_CHUNK_SLACK;
        int maxX = ((origin.getX() + range) >> 4) + START_CHUNK_SLACK;
        int minZ = ((origin.getZ() - range) >> 4) - START_CHUNK_SLACK;
        int maxZ = ((origin.getZ() + range) >> 4) + START_CHUNK_SLACK;
        List<BoundingBox> boxes = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (center.getChessboardDistance(new ChunkPos(x, z)) > 8 || !region.hasChunk(x, z)) {
                    continue;
                }
                for (Map.Entry<Structure, StructureStart> entry
                        : region.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS).getAllStarts().entrySet()) {
                    if (entry.getValue().isValid() && registry.wrapAsHolder(entry.getKey()).is(TAG)) {
                        boxes.add(entry.getValue().getBoundingBox());
                    }
                }
            }
        }
        return boxes;
    }
}
