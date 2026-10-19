package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.effect.MaidSpellEffects;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** 在最终伤害阶段把虚空漫步持有者受到的虚空伤害降低 95%。 */
public final class StaranchorPearlEvents {

    private StaranchorPearlEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!VoidWalkEffect.isVoidDamage(event.getSource())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(MaidSpellEffects.VOID_WALK)) {
            return;
        }
        event.setNewDamage(event.getNewDamage() * (1.0F - VoidWalkEffect.VOID_DAMAGE_REDUCTION));
    }
}
