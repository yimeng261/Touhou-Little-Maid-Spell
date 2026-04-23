package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.resource.models.MaidModels;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(value = MaidModels.class, remap = false)
public abstract class MaidModelsMergeMixin {
    private static final ResourceLocation EMPTY_ICON = new ResourceLocation("touhou_little_maid", "textures/gui/empty_model_pack_icon.png");

    @Shadow @Final private List<CustomModelPack<MaidModelInfo>> packList;
    @Shadow @Final private HashMap<String, BedrockModel<Mob>> idModelMap;
    @Shadow @Final private HashMap<String, MaidModelInfo> idInfoMap;
    @Shadow @Final private HashMap<String, List<Object>> idAnimationMap;

    @Inject(method = "addPack", at = @At("HEAD"), cancellable = true)
    private void maidspell$mergePackIfPresent(CustomModelPack<MaidModelInfo> pack, CallbackInfo ci) {
        for (CustomModelPack<MaidModelInfo> existingPack : this.packList) {
            if (!existingPack.getId().equals(pack.getId())) {
                continue;
            }

            Set<String> existingModelIds = new HashSet<>();
            for (MaidModelInfo existingModel : existingPack.getModelList()) {
                existingModelIds.add(existingModel.getModelId().toString());
            }
            for (MaidModelInfo newModel : pack.getModelList()) {
                String newModelId = newModel.getModelId().toString();
                if (!existingModelIds.contains(newModelId)) {
                    existingPack.getModelList().add(newModel);
                    existingModelIds.add(newModelId);
                }
            }
            inheritPackIcon(existingPack, pack);
            ci.cancel();
            return;
        }
    }

    private static void inheritPackIcon(CustomModelPack<MaidModelInfo> existingPack, CustomModelPack<MaidModelInfo> incomingPack) {
        CustomModelPackAccessor existingAccessor = (CustomModelPackAccessor) existingPack;
        ResourceLocation existingIcon = existingAccessor.maidspell$getIcon();
        ResourceLocation incomingIcon = ((CustomModelPackAccessor) incomingPack).maidspell$getIcon();
        if (EMPTY_ICON.equals(existingIcon) && incomingIcon != null && !EMPTY_ICON.equals(incomingIcon)) {
            existingAccessor.maidspell$setIcon(incomingIcon);
        }
    }

    @Inject(method = "putModel", at = @At("HEAD"), cancellable = true)
    private void maidspell$skipExistingModel(String modelId, BedrockModel<Mob> modelJson, CallbackInfo ci) {
        if (this.idModelMap.containsKey(modelId)) {
            ci.cancel();
        }
    }

    @Inject(method = "putInfo", at = @At("HEAD"), cancellable = true)
    private void maidspell$skipExistingInfo(String modelId, MaidModelInfo maidModelItem, CallbackInfo ci) {
        if (this.idInfoMap.containsKey(modelId)) {
            ci.cancel();
        }
    }

    @Inject(method = "putAnimation", at = @At("HEAD"), cancellable = true)
    private void maidspell$skipExistingAnimation(String modelId, List<Object> animationJs, CallbackInfo ci) {
        if (this.idAnimationMap.containsKey(modelId)) {
            ci.cancel();
        }
    }
}
