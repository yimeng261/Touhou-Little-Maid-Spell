package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.accessor.RandomStateAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 RandomState 加所属维度字段，由 {@link ServerLevelMixin} 在维度创建时写入。
 */
@Mixin(RandomState.class)
public abstract class RandomStateMixin implements RandomStateAccessor {

    @Unique
    @Nullable
    private ResourceKey<Level> maidspell$dimensionKey;

    @Override
    public void maidspell$setDimensionKey(ResourceKey<Level> dimensionKey) {
        this.maidspell$dimensionKey = dimensionKey;
    }

    @Override
    @Nullable
    public ResourceKey<Level> maidspell$getDimensionKey() {
        return this.maidspell$dimensionKey;
    }
}
