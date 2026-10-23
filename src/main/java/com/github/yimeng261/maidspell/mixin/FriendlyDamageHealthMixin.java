package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.utils.FriendlyDamageHealthGuard;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FriendlyDamageHealthMixin {
    @Inject(method = "hurt", at = @At("HEAD"))
    private void maidspell$captureFriendlyHurt(DamageSource source, float amount,
                                               CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        FriendlyDamageHealthGuard.onDamageAttempt(self, source);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void maidspell$leaveFriendlyHurt(DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        FriendlyDamageHealthGuard.leaveDamageAttempt((LivingEntity) (Object) this, source);
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void maidspell$captureFriendlyActualHurt(DamageSource source, float amount,
                                                     CallbackInfo ci) {
        FriendlyDamageHealthGuard.onDamageAttempt((LivingEntity) (Object) this, source);
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("HEAD"))
    private void maidspell$restoreFriendlyHealth(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        FriendlyDamageHealthGuard.restoreIfNeeded((LivingEntity) (Object) this);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void maidspell$blockFriendlyDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (FriendlyDamageHealthGuard.shouldBlockDeath(self, source)) {
            FriendlyDamageHealthGuard.restoreIfNeeded(self);
            ci.cancel();
        }
    }
}
