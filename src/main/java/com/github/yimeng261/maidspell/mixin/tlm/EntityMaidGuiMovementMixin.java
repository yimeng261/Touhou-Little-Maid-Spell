package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidNavigationManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.utils.MaidMovementHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public class EntityMaidGuiMovementMixin {
    @Shadow
    public boolean guiOpening;

    @Inject(method = "aiStep()V", at = @At("HEAD"), remap = true)
    private void maidspell$clearResidualMovementWhenGuiOpen(CallbackInfo ci) {
        if (guiOpening) {
            MaidMovementHelper.stopAllMovement((EntityMaid) (Object) this);
        }
    }

    @Redirect(
            method = "aiStep()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/MaidNavigationManager;tick()V",
                    remap = false
            ),
            remap = true
    )
    private void maidspell$skipNavigationManagerTickWhileGuiOpening(MaidNavigationManager navigationManager) {
        if (!guiOpening) {
            navigationManager.tick();
        }
    }

    @Inject(method = "canBrainMoving()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$blockBrainMovementChecksWhileGuiOpening(CallbackInfoReturnable<Boolean> cir) {
        if (guiOpening) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "customServerAiStep()V", at = @At("TAIL"), remap = true)
    private void maidspell$stopCastingAfterCustomServerAiStep(CallbackInfo ci) {
        if (!guiOpening) {
            return;
        }

        EntityMaid maid = (EntityMaid) (Object) this;
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        manager.stopAllCasting();
        maid.getNavigation().stop();
        maid.getMoveControl().strafe(0, 0);
    }
}
