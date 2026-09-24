package com.github.yimeng261.maidspell.api;

import net.minecraft.world.entity.LivingEntity;

/** Health used by this mod's combat pipeline, independent of public health views. */
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

    static boolean deadOrDying(LivingEntity entity) {
        return entity instanceof IAuthoritativeHealth authority
            ? authority.maidspell$authoritativeHealth() <= 0.0F : entity.isDeadOrDying();
    }
}
