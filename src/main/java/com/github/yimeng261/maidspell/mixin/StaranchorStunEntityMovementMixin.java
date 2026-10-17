package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.effect.StaranchorStunEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class StaranchorStunEntityMovementMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void maidspell$holdStunnedPosition(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof LivingEntity living && StaranchorStunEffect.isStunned(living)) {
            living.setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }
}
