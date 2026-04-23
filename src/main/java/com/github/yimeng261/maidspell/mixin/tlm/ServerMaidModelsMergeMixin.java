package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.info.models.ServerMaidModels;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

@Mixin(value = ServerMaidModels.class, remap = false)
public abstract class ServerMaidModelsMergeMixin {
    @Shadow @Final private HashMap<String, MaidModelInfo> idInfoMap;

    @Inject(method = "putInfo", at = @At("HEAD"), cancellable = true)
    private void maidspell$skipExistingInfo(String modelId, MaidModelInfo maidModelItem, CallbackInfo ci) {
        if (this.idInfoMap.containsKey(modelId)) {
            ci.cancel();
        }
    }
}
