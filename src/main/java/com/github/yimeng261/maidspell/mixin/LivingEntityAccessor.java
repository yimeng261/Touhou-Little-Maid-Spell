package com.github.yimeng261.maidspell.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> getDataHealthIdAccessor() {
        throw new AssertionError();
    }

    @Accessor("lastHurt")
    float maidspell$getLastHurt();

    @Accessor("lastHurt")
    void maidspell$setLastHurt(float value);
}
