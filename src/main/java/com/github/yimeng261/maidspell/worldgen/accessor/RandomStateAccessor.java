package com.github.yimeng261.maidspell.worldgen.accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/**
 * 给 RandomState 记上所属维度。
 * 每个 ChunkMap 各建一份 RandomState，并随生成上下文传给结构；ChunkGenerator 则可能被同一 LevelStem 建出的多个维度共用，
 * 所以维度信息挂在 RandomState 上。
 */
public interface RandomStateAccessor {
    void maidspell$setDimensionKey(ResourceKey<Level> dimensionKey);

    @Nullable
    ResourceKey<Level> maidspell$getDimensionKey();

    /** 取 RandomState 所属维度；不是 ServerLevel 的 ChunkMap 建的时为 null。 */
    @Nullable
    static ResourceKey<Level> dimensionOf(RandomState randomState) {
        return ((RandomStateAccessor) (Object) randomState).maidspell$getDimensionKey();
    }
}
