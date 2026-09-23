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

/**
 * A defeated duel maid is held in place for the remainder of the encounter.
 * Stop the TLM interaction entry point before its smart-slab handler can
 * discard the entity.
 *
 * <p>判断依据走 {@link MagicalWinefoxBossEntity#isRetiredMaid}：它同时看 ForgeData 标记
 * 和 {@code EntityMaidWinefoxRetiredStateMixin} 加的那个同步位，所以客户端也判得出来。
 * 这一点是必须的 —— 客户端在 {@code Player.interactOn} 里会本地预测跑一遍
 * {@code mobInteract}，只看 ForgeData 的话客户端永远放行，魂符会把渲染删掉而实体留在服务端。
 */
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
