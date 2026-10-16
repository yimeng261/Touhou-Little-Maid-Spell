package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.effect.StaranchorStunEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class StaranchorStunLivingEntityMixin {
    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
    private void maidspell$stopStunnedAi(CallbackInfoReturnable<Boolean> cir) {
        if (StaranchorStunEffect.isStunned((LivingEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
