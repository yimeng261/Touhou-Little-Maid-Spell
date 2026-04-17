package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.world.entity.ai.control.JumpControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JumpControl.class)
public interface JumpControlAccessor {
    @Accessor("jump")
    @Mutable
    void maidspell$setJump(boolean value);
}
