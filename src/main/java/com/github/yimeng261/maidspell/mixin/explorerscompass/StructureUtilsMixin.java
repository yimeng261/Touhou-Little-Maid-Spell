package com.github.yimeng261.maidspell.mixin.explorerscompass;

import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让以 UUID 命名的独立归隐之地在探险家指南针里显示统一、可读的名字。 */
@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.util.StructureUtils", remap = false)
public abstract class StructureUtilsMixin {
    @Inject(method = "getDimensionName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$namePrivateRetreat(ResourceLocation dimension,
                                                    CallbackInfoReturnable<String> callback) {
        if (TheRetreatDimension.isPrivateRetreatDimension(dimension)) {
            callback.setReturnValue(Component.translatable(
                    "dimension.touhou_little_maid_spell.independent_retreat").getString());
        }
    }
}
