package com.github.yimeng261.maidspell.utils;

import net.minecraft.world.entity.LivingEntity;

public final class DamageAbsorption {
    private DamageAbsorption() {
    }

    /** 这次伤害里会先被吸收生命值抵掉的部分。 */
    public static float absorbedPart(LivingEntity victim, float damage) {
        return Math.max(0.0F, Math.min(victim.getAbsorptionAmount(), damage));
    }
}
