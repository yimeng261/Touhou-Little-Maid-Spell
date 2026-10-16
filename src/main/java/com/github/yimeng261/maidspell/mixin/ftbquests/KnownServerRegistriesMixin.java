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

/** Keeps the any-retreat condition selectable when only private dimensions exist. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.KnownServerRegistries", remap = false)
public abstract class KnownServerRegistriesMixin {
    @Shadow
    @Final
    public List<ResourceLocation> dimensions;

    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;)V",
            at = @At("RETURN"), remap = false)
    private void maidspell$includeRetreatTaskDimension(CallbackInfo callback) {
        ResourceLocation retreat = TheRetreatDimension.getSharedRetreatDimension().location();
        if (!dimensions.contains(retreat)) {
            dimensions.add(retreat);
            dimensions.sort(null);
        }
    }
}
