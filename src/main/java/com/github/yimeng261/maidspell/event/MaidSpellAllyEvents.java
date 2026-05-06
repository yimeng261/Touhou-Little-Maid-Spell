package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Global friendly target and damage guard for maids, owners, and compatible summons.
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class MaidSpellAllyEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget == null) {
            return;
        }
        LivingEntity attacker = event.getEntity();
        if (MaidSpellAllyResolver.areFriendly(attacker, newTarget)) {
            event.setCanceled(true);
            clearFriendlyTarget(attacker, newTarget);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (isFriendlyDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            clearFriendlyDamageMemory(event.getEntity(), event.getSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (isFriendlyDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            clearFriendlyDamageMemory(event.getEntity(), event.getSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (isFriendlyDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            clearFriendlyDamageMemory(event.getEntity(), event.getSource());
        }
    }

    private static boolean isFriendlyDamage(LivingEntity target, DamageSource source) {
        Entity causing = source.getEntity();
        Entity direct = source.getDirectEntity();
        return MaidSpellAllyResolver.isFriendlyDamage(target, causing, direct);
    }

    private static void clearFriendlyDamageMemory(LivingEntity target, DamageSource source) {
        Entity causing = source.getEntity();
        Entity direct = source.getDirectEntity();
        clearFriendlyTarget(target, causing);
        clearFriendlyTarget(target, direct);
        MaidSpellAllyResolver.resolveResponsibleEntity(direct).ifPresent(owner -> clearFriendlyTarget(target, owner));
    }

    private static void clearFriendlyTarget(Entity first, Entity second) {
        if (first == null || second == null) {
            return;
        }
        if (first instanceof Mob firstMob && firstMob.getTarget() == second) {
            firstMob.setTarget(null);
        }
        if (second instanceof Mob secondMob && secondMob.getTarget() == first) {
            secondMob.setTarget(null);
        }
        if (first instanceof LivingEntity firstLiving && second instanceof LivingEntity secondLiving) {
            if (firstLiving.getLastHurtByMob() == secondLiving) {
                firstLiving.setLastHurtByMob(null);
            }
            if (firstLiving.getLastHurtMob() == secondLiving) {
                firstLiving.setLastHurtMob(null);
            }
            if (secondLiving.getLastHurtByMob() == firstLiving) {
                secondLiving.setLastHurtByMob(null);
            }
            if (secondLiving.getLastHurtMob() == firstLiving) {
                secondLiving.setLastHurtMob(null);
            }
        }
    }
}
