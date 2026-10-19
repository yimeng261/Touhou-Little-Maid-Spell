package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.FriendlyDamageHealthGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityMaid.class)
public abstract class MaidFriendlyDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"))
    private void maidspell$captureFriendlyHurt(DamageSource source, float amount,
                                               CallbackInfoReturnable<Boolean> cir) {
        FriendlyDamageHealthGuard.onDamageAttempt((LivingEntity) (Object) this, source);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void maidspell$leaveFriendlyHurt(DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        FriendlyDamageHealthGuard.leaveDamageAttempt((LivingEntity) (Object) this, source);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void maidspell$blockFriendlyDeath(DamageSource source, CallbackInfo ci) {
        EntityMaid self = (EntityMaid) (Object) this;
        if (FriendlyDamageHealthGuard.shouldBlockDeath(self, source)) {
            FriendlyDamageHealthGuard.restoreIfNeeded(self);
            ci.cancel();
        }
    }
}
