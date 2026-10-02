package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxNonLethalGuard;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.VoidPhaseSpell;
import com.github.yimeng261.maidspell.mixin.LivingEntityAccessor;
import com.github.yimeng261.maidspell.utils.DamageAbsorption;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

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
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (APPLYING_VOID_DAMAGE.get() || event.getEntity().level().isClientSide) {
            return;
        }
        // Pre 对被盾完全格挡或被其它处理器清零的伤害也会触发，这些不算命中
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        // 追加的那一发自己也会走这个事件，靠上面的闸门挡住，否则会无限递归。
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        MobEffectInstance phase = attacker.getEffect(IronsSpellbooksCompatEffects.VOID_PHASE);
        if (phase == null) {
            return;
        }

        int spellLevel = phase.getAmplifier() + 1;
        VoidPhaseSpell spell = (VoidPhaseSpell) IronsSpellbooksCompatSpells.VOID_PHASE.get();
        float bonusDamage = spell.getBonusDamage(spellLevel, attacker);
        // 正赛里追加伤害与主伤害共用"削到地板为止"的额度；本处理器优先级最低，拿到的是守则削过的数。
        // Pre 阶段尚未扣吸收，主伤害只按吸收之后真正扣血的部分计入。
        float pendingDamage = event.getNewDamage()
                - DamageAbsorption.absorbedPart(event.getEntity(), event.getNewDamage());
        bonusDamage = WinefoxNonLethalGuard.duelFollowUpLimit(
                event.getEntity(), event.getSource(), pendingDamage, bonusDamage);
        if (bonusDamage <= 0.0F) {
            return;
        }

        APPLYING_VOID_DAMAGE.set(true);
        // 追加伤害走一次完整的 hurt，会把 lastHurt 改成追加量；受击间隔内的下一击按 lastHurt 比较，必须还原成主伤害。
        LivingEntityAccessor target = (LivingEntityAccessor) event.getEntity();
        float previousLastHurt = target.maidspell$getLastHurt();
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
            target.maidspell$setLastHurt(previousLastHurt);
            event.getEntity().invulnerableTime = previousInvulnerabilityTime;
            event.getEntity().setDeltaMovement(previousMotion);
            APPLYING_VOID_DAMAGE.set(false);
        }
    }
}
