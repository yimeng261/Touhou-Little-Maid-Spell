package com.github.yimeng261.maidspell.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 星落之庭补生成时检查存档里已经存在的其他结构，避免把庭院放进别的结构里。
 *
 * <p>冲突判定带高度：只有结构包围盒和庭院包围盒在竖直方向真正重叠才算冲突。矿井、古城等
 * 结构虽然在同一批区块里留有起点，但整体在庭院下方，不拦补生成。
 *
 * <p>轻量版刻意不解析 {@code References}：那属于「起点在远处、结构片段伸进候选区块」的情况，
 * 需要额外加载起始区块才能拿到高度，留待后续再做。
 */
public final class StarfallGardenPlacementGuard {
    private static final int CHUNK_RADIUS = 1;

    private StarfallGardenPlacementGuard() {
    }

    /**
     * 仅供补生成在服务器主线程、已预约庭院名额后调用。
     * 只请求 {@code STRUCTURE_STARTS}，不为避让检查生成地形或方块。
     *
     * @param gardenBox 庭院自己的包围盒，由已经算好的 {@code StructureStart} 提供；
     *                  没有它就做不了高度判定，所以必须由调用方传入
     */
    public static boolean hasExistingNearbyStructure(ServerLevel level, Structure garden, ChunkPos candidate,
                                                     BoundingBox gardenBox) {
        for (int x = candidate.x - CHUNK_RADIUS; x <= candidate.x + CHUNK_RADIUS; x++) {
            for (int z = candidate.z - CHUNK_RADIUS; z <= candidate.z + CHUNK_RADIUS; z++) {
                ChunkAccess chunk = level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, true);
                if (hasOtherStructure(chunk, garden, gardenBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasOtherStructure(ChunkAccess chunk, Structure garden, BoundingBox gardenBox) {
        return chunk.getAllStarts().entrySet().stream()
                .anyMatch(entry -> entry.getKey() != garden
                        && entry.getValue().isValid()
                        && entry.getValue().getBoundingBox().intersects(gardenBox));
    }
}
