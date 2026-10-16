package com.github.yimeng261.maidspell.worldgen;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StellarEndshoreFixedSpawnTest {
    @Test
    void seedSelectionAlwaysUsesOneOfTheFourCardinalAnchorsAtTheRequestedHeight() {
        Set<BlockPos> anchors = Set.of(
                new BlockPos(800, 150, 0),
                new BlockPos(-800, 200, 0),
                new BlockPos(0, 200, 800),
                new BlockPos(0, 200, -800));
        for (long seed : new long[]{0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            BlockPos anchor = StellarEndshoreFixedSpawn.selectAnchor(seed);
            assertEquals(true, anchors.contains(anchor));
        }
    }
}
