package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.effect.MaidSpellEffects;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「虚空漫步」期间的虚空伤害免疫。
 *
 * <p>{@code minecraft:out_of_world} 是在 {@code LivingEntity.hurt} 里、护甲和吸收之前
 * 就被 {@code actuallyHurt} 处理的，而且它带着 {@code bypasses_invulnerability} ——
 * {@code setInvulnerable}、抗性提升、伤害吸收统统拦不住它。能拦住的只有「不 hurt」这一条路，
 * 所以在 {@link LivingAttackEvent} 上取消。
 *
 * <p>选 {@code LivingAttackEvent} 而不是 {@code LivingHurtEvent}/{@code LivingDamageEvent}，
 * 是因为它发得最早（原版那几句无敌与睡眠判断之后、伤害结算之前），取消掉就是真的一声不响：
 * 不会掉血、不会红屏、不会触发受击音效。掉出世界时这个事件每 tick 都会来一次，
 * 取消只是把伤害挡掉，人还站在虚空里 —— 「免疫伤害」不等于「把人捞出来」。
 */
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
