package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityInvoker {
    @Invoker("unsetRemoved")
    void maidspell$invokeUnsetRemoved();
}
