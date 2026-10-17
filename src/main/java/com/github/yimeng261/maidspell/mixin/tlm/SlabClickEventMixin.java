package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.event.maid.SlabClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁止客户端预测魂符收女仆；TLM 会在本地直接 discard 实体，
 * 服务端拒绝交互时会造成客户端实体消失而服务端仍保留。
 */
@Mixin(value = SlabClickEvent.class, remap = false)
public class SlabClickEventMixin {
    @Inject(method = "onInteract", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$skipClientStorePrediction(InteractMaidEvent event, CallbackInfo ci) {
        if (event.getMaid().level().isClientSide()) {
            ci.cancel();
        }
    }
}
