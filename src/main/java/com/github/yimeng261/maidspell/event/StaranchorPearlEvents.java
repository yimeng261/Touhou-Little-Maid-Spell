package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.effect.MaidSpellEffects;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 在最终伤害阶段把虚空漫步持有者受到的虚空伤害降低 95%。 */
public final class StaranchorPearlEvents {

    private StaranchorPearlEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!VoidWalkEffect.isVoidDamage(event.getSource())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        MobEffect voidWalk = MaidSpellEffects.VOID_WALK.get();
        if (voidWalk == null || !entity.hasEffect(voidWalk)) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F - VoidWalkEffect.VOID_DAMAGE_REDUCTION));
    }
}
