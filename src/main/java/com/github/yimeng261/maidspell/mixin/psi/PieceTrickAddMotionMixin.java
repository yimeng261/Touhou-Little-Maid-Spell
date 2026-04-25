package com.github.yimeng261.maidspell.mixin.psi;

import com.github.yimeng261.maidspell.spell.providers.PsiProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vazkii.psi.api.internal.Vector3;
import vazkii.psi.api.spell.SpellContext;
import vazkii.psi.api.spell.SpellRuntimeException;
import vazkii.psi.common.spell.trick.entity.PieceTrickAddMotion;

@Mixin(value = PieceTrickAddMotion.class, remap = false)
public abstract class PieceTrickAddMotionMixin {
    @Inject(method = "addMotion", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$blockPlayerMotionFromMaidPsi(SpellContext context, Entity entity, Vector3 dir, double speed,
                                                               CallbackInfo ci) throws SpellRuntimeException {
        if (entity instanceof Player && PsiProvider.getMaidUuidFromCaster(context.caster) != null) {
            context.verifyEntity(entity);
            ci.cancel();
        }
    }
}
