package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation.PalmBlackHoleRippleAlignment;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GeckoMaidEntity.class, remap = false)
public abstract class PalmBlackHoleRippleAlignmentMixin {
    @Inject(method = "setCustomAnimations", at = @At("RETURN"))
    private void maidspell$alignPalmRipples(AnimationContext context, AnimationEvent event,
                                           CallbackInfoReturnable<Boolean> result) {
        // A skipped animation update reuses the previously corrected bones.
        if (!result.getReturnValueZ()) return;
        GeckoMaidEntity<?> animated = (GeckoMaidEntity<?>) (Object) this;
        if (!(animated.getEntity() instanceof MagicalWinefoxBossEntity witch)
                || !witch.isCastingPalmBlackHole()) return;
        for (PalmBlackHoleEntity ritual : witch.level().getEntitiesOfClass(PalmBlackHoleEntity.class,
                witch.getBoundingBox().inflate(48), entity -> entity.belongsTo(witch))) {
            PalmBlackHoleRippleAlignment.align(animated.getCurrentModel(), ritual.visualAge(event.getPartialTick()));
            break;
        }
    }
}
