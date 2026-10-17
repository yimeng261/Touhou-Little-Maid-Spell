package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.effect.MaidSpellEffects;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** 在 LivingIncomingDamageEvent 阶段取消虚空伤害，避免后续伤害与受击效果。 */
public final class StaranchorPearlEvents {

    private StaranchorPearlEvents() {
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!VoidWalkEffect.isVoidDamage(event.getSource())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(MaidSpellEffects.VOID_WALK)) {
            return;
        }
        event.setCanceled(true);
    }
}
