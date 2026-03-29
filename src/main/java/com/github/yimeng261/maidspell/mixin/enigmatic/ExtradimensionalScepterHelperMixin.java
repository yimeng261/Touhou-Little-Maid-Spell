package com.github.yimeng261.maidspell.mixin.enigmatic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
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
        if (entity instanceof EntityMaid maid && BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
            ci.cancel();
        }
    }
}
