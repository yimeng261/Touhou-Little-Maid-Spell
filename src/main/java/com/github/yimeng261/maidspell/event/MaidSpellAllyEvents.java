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
import org.jetbrains.annotations.Nullable;

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
        if (isSuppressedMaidSide(attacker) && inSuppressedFight(attacker, newTarget)) {
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
     * <p>拦的是"女仆这一边造成的伤害"，不分对象——擂台是玩家一个人的，
     * 圈里的女仆连顺手清个小怪都不该做。"这一边"包含她的召唤物，
     * 而"圈里"出手方和挨打方各算一次，见 {@link #isSuppressedMaidSide}
     * 与 {@link #inSuppressedFight}。区域只在驯服挑战期间存在，见
     * {@link MaidSuppressionZone}。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSuppressedMaidAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        Entity origin = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (isSuppressedMaidSide(origin) && inSuppressedFight(origin, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 这个实体算不算「女仆这一边」—— 女仆本人，或者顺着 owner 链能追到女仆的召唤物。
     *
     * <p>召唤物不算的话，把召唤兽留在场上就绕开了整条压制规则。
     */
    private static boolean isSuppressedMaidSide(@Nullable Entity entity) {
        if (entity instanceof EntityMaid) {
            return true;
        }
        return MaidSpellAllyResolver.resolveResponsibleEntity(entity)
            .filter(EntityMaid.class::isInstance)
            .isPresent();
    }

    /**
     * 出手方或者挨打方，只要有一头站在压制区里，这一下就不算数。
     *
     * <p>两头都看是必要的：压制区是以 Boss 为心的 40 格球，而女仆的远程手段够得着
     * 更远。只按出手方的位置判，站在圈外放法术的女仆照打不误 —— 而她打出的伤害
     * 仍然会计进 {@code maidDamageTaken}，玩家反倒因为一场本该被拦住的插手被判了代打。
     */
    private static boolean inSuppressedFight(@Nullable Entity attacker, @Nullable Entity victim) {
        return MaidSuppressionZone.suppresses(attacker) || MaidSuppressionZone.suppresses(victim);
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
