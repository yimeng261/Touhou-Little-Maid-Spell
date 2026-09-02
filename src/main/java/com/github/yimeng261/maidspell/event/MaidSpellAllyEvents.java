package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.utils.MaidSuppressionZone;
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
        if (attacker instanceof EntityMaid && MaidSuppressionZone.suppresses(attacker)) {
            event.setCanceled(true);
            attacker.setLastHurtByMob(null);
            return;
        }
        if (MaidSpellAllyResolver.areFriendly(attacker, newTarget)) {
            event.setCanceled(true);
            clearFriendlyTarget(attacker, newTarget);
        }
    }

    /**
     * 压制区里的女仆一律打不出伤害。
     *
     * <p>光拦索敌不够：飞在半空的箭、已经抬起的刀、别的模组直接调 {@code hurt} 的路径，
     * 都不经过 {@link LivingChangeTargetEvent}。这一条是兜底。
     *
     * <p>拦的是"女仆造成的伤害"，不分对象——擂台是玩家一个人的，
     * 圈里的女仆连顺手清个小怪都不该做。区域只在驯服挑战期间存在，见
     * {@link MaidSuppressionZone}。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSuppressedMaidAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof EntityMaid maid
                && MaidSuppressionZone.suppresses(maid)) {
            event.setCanceled(true);
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
