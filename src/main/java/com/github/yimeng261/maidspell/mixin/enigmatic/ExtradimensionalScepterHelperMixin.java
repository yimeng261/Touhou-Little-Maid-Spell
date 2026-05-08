package com.github.yimeng261.maidspell.mixin.enigmatic;

import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.ExtradimensionalScepter$Helper", remap = false)
public class ExtradimensionalScepterHelperMixin {
    @Inject(method = "setAll", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$blockAnchoredMaidSetAll(ItemStack stack, CompoundTag tag, LivingEntity entity,
                                                          CallbackInfo ci) {
        if (AnchorCoreProtection.shouldBlockCapture(entity)) {
            ci.cancel();
        }
    }
}
