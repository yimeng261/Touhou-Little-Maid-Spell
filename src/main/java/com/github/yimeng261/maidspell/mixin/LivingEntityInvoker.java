package com.github.yimeng261.maidspell.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("actuallyHurt")
    void maidspell$invokeActuallyHurt(DamageSource damageSource, float damage);

    @Invoker("tickDeath")
    void maidspell$invokeTickDeath();
}
