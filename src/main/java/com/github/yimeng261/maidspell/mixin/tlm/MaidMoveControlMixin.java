package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.control.MaidMoveControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidMoveControl.class, remap = false)
public class MaidMoveControlMixin {
    @Shadow
    @Final
    private EntityMaid maid;

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, remap = true)
    private void maidspell$stopMoveControlWhileGuiOpening(CallbackInfo ci) {
        if (!maid.guiOpening) {
            return;
        }

        maid.setXxa(0.0F);
        maid.setYya(0.0F);
        maid.setZza(0.0F);
        maid.setSpeed(0.0F);
        ci.cancel();
    }
}
