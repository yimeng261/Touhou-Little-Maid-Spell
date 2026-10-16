package com.github.yimeng261.maidspell.worldgen;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;

/** 给场景测试用的同包访问口：星落之庭补生成时判断区块里已存结构的方法是包内可见。 */
public final class WorldgenTestAccess {
    private WorldgenTestAccess() {
    }

    public static boolean hasOtherStructure(ChunkAccess chunk, Structure garden) {
        return StarfallGardenPlacementGuard.hasOtherStructure(chunk, garden);
    }
}
