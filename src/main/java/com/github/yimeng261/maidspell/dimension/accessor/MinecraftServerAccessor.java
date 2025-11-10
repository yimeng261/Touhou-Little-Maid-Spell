package com.github.yimeng261.maidspell.dimension.accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Accessor接口，用于访问MinecraftServer的内部方法
 * 通过Mixin注入实现
 */
public interface MinecraftServerAccessor {
    /**
     * 创建新的世界/维度
     * @param key 世界的ResourceKey
     * @param dimensionTypeKey 维度类型的ResourceLocation
     * @return 是否创建成功
     */
    boolean maidspell$createWorld(ResourceKey<Level> key, ResourceLocation dimensionTypeKey);
    
    /**
     * 移除世界/维度
     * @param key 要移除的世界的ResourceKey
     */
    void maidspell$removeWorld(ResourceKey<Level> key);
}

