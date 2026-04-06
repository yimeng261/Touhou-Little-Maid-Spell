package com.github.yimeng261.maidspell.worldgen.accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * ChunkGenerator的Accessor接口
 * 用于存储和获取维度信息
 */
public interface ChunkGeneratorAccessor {
    /**
     * 设置当前ChunkGenerator所属的维度
     */
    void maidspell$setDimensionKey(ResourceKey<Level> dimensionKey);
    
    /**
     * 获取当前ChunkGenerator所属的维度
     */
    @Nullable
    ResourceKey<Level> maidspell$getDimensionKey();
}

