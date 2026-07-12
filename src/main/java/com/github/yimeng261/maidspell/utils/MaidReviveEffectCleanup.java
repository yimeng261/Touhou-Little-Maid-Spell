package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Map;

public final class MaidReviveEffectCleanup {
    private static final double MIN_VALID_MOVEMENT_SPEED_BASE = 1.0E-4D;
    private static final double DEFAULT_MOVEMENT_SPEED_BASE = Attributes.MOVEMENT_SPEED.value().getDefaultValue();

    private MaidReviveEffectCleanup() {
    }

    public static Map<Holder<MobEffect>, MobEffectInstance> rememberEffectsBeforeBaubleRevive(EntityMaid maid) {
        Map<Holder<MobEffect>, MobEffectInstance> effects = new HashMap<>();
        for (MobEffectInstance effect : maid.getActiveEffects()) {
            effects.put(effect.getEffect(), effect);
        }
        return effects;
    }

    public static void cleanupAfterBaubleRevive(
            EntityMaid maid,
            Map<Holder<MobEffect>, MobEffectInstance> effectsBeforeRevive) {
        if (maid.level().isClientSide()) {
            return;
        }

        clearDeathState(maid);
        for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : effectsBeforeRevive.entrySet()) {
            if (maid.getEffect(entry.getKey()) == entry.getValue()) {
                maid.removeEffect(entry.getKey());
            }
        }
        cleanupResidualMovementState(maid);
    }

    public static void cleanupBeforeNormalDeath(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }

        clearDeathState(maid);
        maid.removeAllEffects();
        cleanupResidualMovementState(maid);
    }

    public static void cleanupAfterCanceledDeath(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }

        clearDeathState(maid);
        cleanupResidualMovementState(maid);
    }

    private static void clearDeathState(EntityMaid maid) {
        maid.clearFire();
        maid.setTicksFrozen(0);
        maid.setSharedFlagOnFire(false);
    }

    private static void cleanupResidualMovementState(EntityMaid maid) {
        if (maid.isNoAi()) {
            maid.setNoAi(false);
        }

        AttributeInstance movementSpeed = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null && movementSpeed.getBaseValue() < MIN_VALID_MOVEMENT_SPEED_BASE) {
            movementSpeed.setBaseValue(DEFAULT_MOVEMENT_SPEED_BASE);
        }

        maid.getNavigation().stop();
        maid.setSpeed((float) maid.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }
}
