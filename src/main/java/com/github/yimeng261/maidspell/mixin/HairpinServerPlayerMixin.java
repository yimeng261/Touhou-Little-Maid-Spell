package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class HairpinServerPlayerMixin {
    @Inject(method = "indicateDamage", at = @At("HEAD"), cancellable = true)
    private void maidspell$cancelHairpinSampleIndicateDamage(double x, double z, CallbackInfo ci) {
        if (HairpinBauble.isSamplingOwnerDamage((ServerPlayer) (Object) this)) {
            ci.cancel();
        }
    }
}
