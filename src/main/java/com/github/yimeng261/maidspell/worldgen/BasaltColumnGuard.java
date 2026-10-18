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
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 玄武岩柱不长进 {@link #TAG} 里结构的占地：底框水平外扩 {@link #MARGIN} 格、从底框最低处往上的范围内不放玄武岩。
 * 玄武岩柱是地表结构放完之后才生成的地物，不拦的话会把建在玄武岩三角洲里的结构整个包住。
 * 只拦落进这个范围的方块，范围外的柱子照常生成。
 */
public final class BasaltColumnGuard {
    public static final TagKey<Structure> TAG = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "no_basalt_columns"));
    private static final int MARGIN = 2;
    /** 一簇玄武岩柱离起点最远的水平距离：散布 8 格加单根柱子的半径 3 格。 */
    private static final int REACH = 11;
    /** 结构起点区块离结构底框的最远距离（区块数），超出的结构起点不用看。 */
    private static final int START_CHUNK_SLACK = 1;

    private static final ThreadLocal<List<BoundingBox>> PROTECTED = ThreadLocal.withInitial(List::of);

    private BasaltColumnGuard() {
    }

    /** 一簇玄武岩柱开始放之前，找出它够得着的受保护结构。 */
    public static void begin(WorldGenLevel level, BlockPos origin) {
        PROTECTED.set(find(level, origin));
    }

    public static void end() {
        PROTECTED.remove();
    }

    /** pos 是否落在受保护结构的范围里。 */
    public static boolean blocked(BlockPos pos) {
        for (BoundingBox box : PROTECTED.get()) {
            if (pos.getY() >= box.minY()
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
    private static List<BoundingBox> find(WorldGenLevel level, BlockPos origin) {
        if (!(level instanceof WorldGenRegion region)) {
            return List.of();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkPos center = region.getCenter();
        int range = REACH + MARGIN;
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
