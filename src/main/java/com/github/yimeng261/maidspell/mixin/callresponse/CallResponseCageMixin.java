package com.github.yimeng261.maidspell.mixin.callresponse;

import com.github.yimeng261.maidspell.utils.PersistentEntityLifecycleGuard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlockEntity", remap = false)
public abstract class CallResponseCageMixin {
    @Inject(method = "capture", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$blockProtectedCapture(LivingEntity living, Player captor,
                                                 @Coerce Object origin,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (PersistentEntityLifecycleGuard.shouldBlockCapture(living)) {
            cir.setReturnValue(false);
        }
    }
}
