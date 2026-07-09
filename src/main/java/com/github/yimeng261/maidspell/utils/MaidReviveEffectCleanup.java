package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MaidReviveEffectCleanup {
    private static final UUID GOETY_CRIPPLED_MOVEMENT_SPEED_UUID = goetyEffectUuid("crippled.movement_speed");
    private static final UUID GOETY_CRIPPLED_ATTACK_SPEED_UUID = goetyEffectUuid("crippled.attack_speed");
    private static final UUID GOETY_CRIPPLED_ATTACK_DAMAGE_UUID = goetyEffectUuid("crippled.attack_damage");
    private static final UUID GOETY_STUNNED_SWIM_SPEED_UUID = UUID.fromString("e4669259-9b6f-40d2-b253-46e65b1f3363");
    private static final UUID GOETY_STUNNED_MOVEMENT_SPEED_UUID = UUID.fromString("963d8748-941f-4f75-b4a6-a9c85013f27f");
    private static final UUID GOETY_TANGLED_SWIM_SPEED_UUID = UUID.fromString("862219f1-18f4-483a-94db-0d4c6c4fdef2");
    private static final UUID GOETY_TANGLED_MOVEMENT_SPEED_UUID = UUID.fromString("8246d3de-e765-487d-adda-18a1deb3e4a9");

    private MaidReviveEffectCleanup() {
    }

    public static Map<MobEffect, MobEffectInstance> rememberEffectsBeforeBaubleRevive(EntityMaid maid) {
        Map<MobEffect, MobEffectInstance> effects = new HashMap<>();
        for (MobEffectInstance effect : maid.getActiveEffects()) {
            effects.put(effect.getEffect(), effect);
        }
        return effects;
    }

    public static void cleanupAfterBaubleRevive(
        EntityMaid maid,
        Map<MobEffect, MobEffectInstance> effectsBeforeRevive
    ) {
        if (maid.level().isClientSide()) {
            return;
        }

        maid.clearFire();
        maid.setTicksFrozen(0);
        maid.setSharedFlagOnFire(false);

        // Keep effects granted by the revive bauble itself, but remove effects that survived from before death.
        removeEffectsPresentBeforeRevive(maid, effectsBeforeRevive);
        cleanupResidualMovementState(maid);
    }

    public static void cleanupBeforeNormalDeath(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }

        maid.clearFire();
        maid.setTicksFrozen(0);
        maid.setSharedFlagOnFire(false);
        maid.removeAllEffects();
        cleanupResidualMovementState(maid);
    }

    private static void cleanupResidualMovementState(EntityMaid maid) {
        removeAttributeModifier(maid, Attributes.MOVEMENT_SPEED, GOETY_CRIPPLED_MOVEMENT_SPEED_UUID);
        removeAttributeModifier(maid, Attributes.ATTACK_SPEED, GOETY_CRIPPLED_ATTACK_SPEED_UUID);
        removeAttributeModifier(maid, Attributes.ATTACK_DAMAGE, GOETY_CRIPPLED_ATTACK_DAMAGE_UUID);
        removeAttributeModifier(maid, Attributes.MOVEMENT_SPEED, GOETY_STUNNED_MOVEMENT_SPEED_UUID);
        removeAttributeModifier(maid, ForgeMod.SWIM_SPEED.get(), GOETY_STUNNED_SWIM_SPEED_UUID);
        removeAttributeModifier(maid, Attributes.MOVEMENT_SPEED, GOETY_TANGLED_MOVEMENT_SPEED_UUID);
        removeAttributeModifier(maid, ForgeMod.SWIM_SPEED.get(), GOETY_TANGLED_SWIM_SPEED_UUID);

        if (maid.isNoAi()) {
            maid.setNoAi(false);
        }

        maid.getNavigation().stop();
        maid.setSpeed((float) maid.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    private static void removeEffectsPresentBeforeRevive(
        EntityMaid maid,
        Map<MobEffect, MobEffectInstance> effectsBeforeRevive
    ) {
        for (Map.Entry<MobEffect, MobEffectInstance> entry : effectsBeforeRevive.entrySet()) {
            MobEffect effect = entry.getKey();
            if (maid.getEffect(effect) == entry.getValue()) {
                maid.removeEffect(effect);
            }
        }
    }

    private static boolean removeAttributeModifier(EntityMaid maid, Attribute attribute, UUID uuid) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null || instance.getModifier(uuid) == null) {
            return false;
        }
        instance.removeModifier(uuid);
        return true;
    }

    private static UUID goetyEffectUuid(String suffix) {
        return UUID.nameUUIDFromBytes(("effect.goety." + suffix).getBytes(StandardCharsets.UTF_8));
    }
}
