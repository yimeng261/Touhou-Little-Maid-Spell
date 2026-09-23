package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.VoidPhaseSpell;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 虚空相变的追加伤害：只要身上带着虚空相变，攻击者造成的任何一次伤害都会再追加一次虚空伤害。
 *
 * <p>没有来源类型筛选 —— 近战、末影法术、箭矢、爆炸、荆棘反伤一律触发，唯一的门槛是
 * 「伤害来源实体是生物且身上有 {@code void_phase}」。
 */
public final class VoidPhaseDamageHandler {
    private static final ThreadLocal<Boolean> APPLYING_VOID_DAMAGE =
            ThreadLocal.withInitial(() -> false);

    private VoidPhaseDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (APPLYING_VOID_DAMAGE.get() || event.getEntity().level().isClientSide) {
            return;
        }

        // 追加的那一发自己也会走这个事件，靠上面的闸门挡住，否则会无限递归。
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        MobEffectInstance phase = attacker.getEffect(IronsSpellbooksCompatEffects.VOID_PHASE.get());
        if (phase == null) {
            return;
        }

        int spellLevel = phase.getAmplifier() + 1;
        VoidPhaseSpell spell = (VoidPhaseSpell) IronsSpellbooksCompatSpells.VOID_PHASE.get();
        float bonusDamage = spell.getBonusDamage(spellLevel, attacker);
        if (bonusDamage <= 0.0F) {
            return;
        }

        APPLYING_VOID_DAMAGE.set(true);
        int previousInvulnerabilityTime = event.getEntity().invulnerableTime;
        Vec3 previousMotion = event.getEntity().getDeltaMovement();
        try {
            event.getEntity().invulnerableTime = 0;
            DamageSource voidDamage = new DamageSource(
                    event.getEntity().damageSources().fellOutOfWorld().typeHolder(), attacker);
            event.getEntity().hurt(voidDamage, bonusDamage);
            MagicManager.spawnParticles(event.getEntity().level(), ParticleHelper.UNSTABLE_ENDER,
                    event.getEntity().getX(), event.getEntity().getY(0.5D), event.getEntity().getZ(),
                    12, 0.25D, 0.35D, 0.25D, 0.08D, true);
        } finally {
            event.getEntity().invulnerableTime = previousInvulnerabilityTime;
            event.getEntity().setDeltaMovement(previousMotion);
            APPLYING_VOID_DAMAGE.set(false);
        }
    }
}
