package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.effect.MaidSpellEffects;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 在 LivingAttackEvent 阶段取消虚空伤害，避免后续伤害与受击效果。 */
public final class StaranchorPearlEvents {

    private StaranchorPearlEvents() {
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!VoidWalkEffect.isVoidDamage(event.getSource())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        MobEffect voidWalk = MaidSpellEffects.VOID_WALK.get();
        if (voidWalk == null || !entity.hasEffect(voidWalk)) {
            return;
        }
        event.setCanceled(true);
    }
}
