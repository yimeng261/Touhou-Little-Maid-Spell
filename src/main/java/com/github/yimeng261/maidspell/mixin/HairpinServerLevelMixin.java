package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class HairpinServerLevelMixin {
    @Inject(method = "broadcastDamageEvent", at = @At("HEAD"), cancellable = true)
    private void maidspell$cancelHairpinSampleDamageEvent(Entity entity, DamageSource damageSource, CallbackInfo ci) {
        if (entity instanceof LivingEntity livingEntity && HairpinBauble.isSamplingOwnerDamage(livingEntity)) {
            ci.cancel();
        }
    }
}
