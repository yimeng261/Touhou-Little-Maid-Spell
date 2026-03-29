package com.github.yimeng261.maidspell.mixin.goety;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.items.magic.EsotericTesseract", remap = false)
public class EsotericTesseractMixin {
    @Inject(method = "onLeftClickEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$blockAnchoredMaidCapture(ItemStack stack, Player player, Entity target,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof EntityMaid maid && BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
            cir.setReturnValue(true);
        }
    }
}
