package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 击败后暂留的女仆在两端都拦截魂符交互，避免客户端预测删除实体。 */
@Mixin(value = EntityMaid.class, remap = false)
public final class EntityMaidWinefoxRetiredInteractionMixin {
    @Inject(
        method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void maidspell$blockRetiredMaidInteraction(
        Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (MagicalWinefoxBossEntity.isRetiredMaid((EntityMaid) (Object) this)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
