package com.github.yimeng261.maidspell.effect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.StaranchorStunSpellInterrupt;
import com.github.yimeng261.maidspell.particle.MaidSpellParticles;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Staranchor pearls stop active actions and hold nearby enemies in place. */
public final class StaranchorStunEffect extends MobEffect {
    public static final double SHOCKWAVE_RADIUS = 6.0D;
    public static final int STUN_TICKS = 6 * 20;

    public StaranchorStunEffect() {
        super(MobEffectCategory.HARMFUL, 0x9991BA);
    }

    public static boolean isStunned(LivingEntity entity) {
        return MaidSpellEffects.STARANCHOR_STUN.isPresent()
            && entity.hasEffect(MaidSpellEffects.STARANCHOR_STUN.get());
    }

    public static void shockwave(ServerLevel level, LivingEntity user) {
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class,
            user.getBoundingBox().inflate(SHOCKWAVE_RADIUS), entity -> entity != user
                && IAuthoritativeHealth.combatAlive(entity) && !entity.isSpectator()
                && !(entity instanceof Player player && player.isCreative())
                && user.distanceToSqr(entity) <= SHOCKWAVE_RADIUS * SHOCKWAVE_RADIUS
                && !MaidSpellAllyResolver.areFriendly(user, entity))) {
            if (enemy.addEffect(new MobEffectInstance(MaidSpellEffects.STARANCHOR_STUN.get(),
                STUN_TICKS, 0, false, false, false), user)) {
                interrupt(enemy);
            }
        }
        Vec3 center = user.position().add(0.0D, 0.15D, 0.0D);
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0D * i / 48;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            level.sendParticles(MaidSpellParticles.VOID_SPELL.get(),
                center.x + dx * SHOCKWAVE_RADIUS, center.y, center.z + dz * SHOCKWAVE_RADIUS,
                0, dx, 0.0D, dz, 0.15D);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        interrupt(entity);
    }

    public static void interrupt(LivingEntity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        entity.stopUsingItem();
        entity.swinging = false;
        entity.swingTime = 0;
        if (entity.level().isClientSide) return;
        entity.hurtMarked = true;
        if (entity instanceof Mob mob) mob.getNavigation().stop();
        if (IronsSpellbooksCompat.isLoaded()) {
            StaranchorStunSpellInterrupt.interrupt(entity);
        }
        if (entity instanceof EntityMaid maid) {
            SpellBookManager.getOrCreateManager(maid).stopAllCasting(maid);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && isStunned(event.getEntity())) event.setCanceled(true);
    }
}
