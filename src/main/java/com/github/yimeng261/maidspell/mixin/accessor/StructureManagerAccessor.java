package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取 {@code StructureManager.level}。{@code ChunkGenerator.getMobsAt} 不带 RandomState，只能从这里认出是哪个维度。
 */
@Mixin(StructureManager.class)
public interface StructureManagerAccessor {
    @Accessor("level")
    LevelAccessor maidspell$getLevel();
}
