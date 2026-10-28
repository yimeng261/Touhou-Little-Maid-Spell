package com.github.yimeng261.maidspell.mixin.ftbquests;

import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** 只存在独立归隐之地时，也让「任意归隐之地」条件所用的占位维度出现在可选列表里。 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.KnownServerRegistries", remap = false)
public abstract class KnownServerRegistriesMixin {
    @Shadow
    @Final
    private List<ResourceLocation> dimension;

    @Inject(method = "<init>(Ljava/util/List;Ljava/util/Map;)V", at = @At("RETURN"), remap = false)
    private void maidspell$includeRetreatTaskDimension(CallbackInfo callback) {
        ResourceLocation retreat = TheRetreatDimension.getSharedRetreatDimension().location();
        if (!dimension.contains(retreat)) {
            dimension.add(retreat);
            dimension.sort(null);
        }
    }
}
