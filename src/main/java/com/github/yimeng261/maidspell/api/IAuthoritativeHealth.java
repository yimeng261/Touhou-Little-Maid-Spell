package com.github.yimeng261.maidspell.api;

import net.minecraft.world.entity.LivingEntity;

/** 本模组战斗逻辑读取的权威生命值，独立于公开的 getHealth */
public interface IAuthoritativeHealth {
    float maidspell$authoritativeHealth();

    static float health(LivingEntity entity) {
        return entity instanceof IAuthoritativeHealth authority
            ? authority.maidspell$authoritativeHealth() : entity.getHealth();
    }

    static boolean combatAlive(LivingEntity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        return entity instanceof IAuthoritativeHealth authority
            ? authority.maidspell$authoritativeHealth() > 0.0F : entity.isAlive();
    }

    /** 仍可作为战斗目标：未移除、权威血量存活且未处于死亡过程 */
    static boolean validCombatTarget(LivingEntity entity) {
        return combatAlive(entity) && !deadOrDying(entity);
    }

    static boolean deadOrDying(LivingEntity entity) {
        return entity instanceof IAuthoritativeHealth authority
            ? authority.maidspell$authoritativeHealth() <= 0.0F : entity.isDeadOrDying();
    }
}
