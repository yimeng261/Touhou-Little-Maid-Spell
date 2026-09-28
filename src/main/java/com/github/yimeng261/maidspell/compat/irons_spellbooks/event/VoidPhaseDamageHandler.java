package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxNonLethalGuard;
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
 * 虚空相变对带效果的生物造成的所有伤害追加虚空伤害。
 * 正式挑战须通过 {@link WinefoxNonLethalGuard#duelFollowUpLimit} 限制追加量，
 * 避免主伤害尚未落账时击穿最低生命值。
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
        // 正赛里这一发要和主伤害共用同一份"削到地板为止"的额度，所以拿的是事件里那个已经被守则
        // 削过的数（本处理器优先级更低，跑在守则后面 —— 顺序由优先级保证，不看注册先后）。
        bonusDamage = WinefoxNonLethalGuard.duelFollowUpLimit(
                event.getEntity(), event.getSource(), event.getAmount(), bonusDamage);
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
