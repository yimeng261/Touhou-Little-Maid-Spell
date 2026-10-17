package com.github.yimeng261.maidspell.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** 星落之庭让出其他结构的起始区块，以及四边和四角相邻的区块。 */
public final class StarfallGardenPlacementGuard {
    private static final int CHUNK_RADIUS = 1;

    private StarfallGardenPlacementGuard() {
    }

    /**
     * 检查当前维度的结构集落点，而不是等其他结构生成成功后再查起点。
     * 因此不受结构集遍历顺序或相邻区块生成先后影响，也不会加载区块或递归调用 generate。
     * 即使其他结构最后因地形或生物群系不合适而失败，也保留它的尝试空间。
     */
    public static boolean hasNearbyStructureAttempt(ChunkGeneratorStructureState state, Structure garden,
                                                    ChunkPos candidate) {
        for (var holder : state.possibleStructureSets()) {
            StructureSet set = holder.value();
            // 仅包含庭院自身的结构集不能把自己挡住；混合结构集仍需避让其他候选。
            if (set.structures().stream().noneMatch(entry -> entry.structure().value() != garden)) {
                continue;
            }
            if (state.hasStructureChunkInRange(holder, candidate.x, candidate.z, CHUNK_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 仅供补生成在服务器主线程、已预约庭院名额后调用。
     * 旧区块可能来自不同的种子落点配置，必须同时检查保存的起点及已有引用。
     * 只请求 STRUCTURE_STARTS，不为避让检查生成地形或方块。
     */
    public static boolean hasExistingNearbyStructure(ServerLevel level, Structure garden, ChunkPos candidate) {
        for (int x = candidate.x - CHUNK_RADIUS; x <= candidate.x + CHUNK_RADIUS; x++) {
            for (int z = candidate.z - CHUNK_RADIUS; z <= candidate.z + CHUNK_RADIUS; z++) {
                ChunkAccess chunk = level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, true);
                if (hasOtherStructure(chunk, garden)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasOtherStructure(ChunkAccess chunk, Structure garden) {
        return chunk.getAllStarts().entrySet().stream()
                .anyMatch(entry -> entry.getKey() != garden && entry.getValue().isValid())
                || chunk.getAllReferences().entrySet().stream()
                .anyMatch(entry -> entry.getKey() != garden && !entry.getValue().isEmpty());
    }
}
