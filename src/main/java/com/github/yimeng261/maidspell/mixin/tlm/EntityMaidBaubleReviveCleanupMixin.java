package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.utils.MaidReviveEffectCleanup;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiPredicate;

@Mixin(value = EntityMaid.class, remap = false)
public class EntityMaidBaubleReviveCleanupMixin {
    @Redirect(
        method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/inventory/handler/BaubleItemHandler;fireEvent(Ljava/util/function/BiPredicate;)Z",
            remap = false
        ),
        remap = true
    )
    private boolean maidspell$cleanupAfterBaubleRevive(
        BaubleItemHandler handler,
        BiPredicate<IMaidBauble, ItemStack> function
    ) {
        EntityMaid maid = (EntityMaid) (Object) this;
        var effectsBeforeRevive = MaidReviveEffectCleanup.rememberEffectsBeforeBaubleRevive(maid);
        boolean canceled = handler.fireEvent(function);
        if (canceled) {
            MaidReviveEffectCleanup.cleanupAfterBaubleRevive(maid, effectsBeforeRevive);
        }
        return canceled;
    }
}
