package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.mixin.LivingEntityAccessor;
import com.github.yimeng261.maidspell.mixin.LivingEntityInvoker;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class TrueDamageUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float HEALTH_TOLERANCE = 0.01f;
    private static final float FAILED_ATTEMPT_GAP = Float.POSITIVE_INFINITY;

    public static boolean dealTrueDamage(LivingEntity target, float damage, LivingEntity attacker) {
        if (target == null) {
            return false;
        }
        float newHealth = Math.max(0.0f, target.getHealth() - damage);
        return setNewHealth(target, newHealth, attacker);
    }

    public static boolean setNewHealth(LivingEntity target, float newHealth, LivingEntity attacker) {
        if (target == null) {
            return false;
        }

        float healthGap = FAILED_ATTEMPT_GAP;

        healthGap = tryEntityDataDamage(target, newHealth);
        if (healthGap <= HEALTH_TOLERANCE) {
            return finishHealthChange(target, newHealth, attacker);
        }

        healthGap = tryNBTDamage(target, newHealth);
        if (healthGap <= HEALTH_TOLERANCE) {
            return finishHealthChange(target, newHealth, attacker);
        }
        return false;
    }

    private static float tryEntityDataDamage(LivingEntity target, float newHealth) {
        try {
            EntityDataAccessor<Float> dataHealthIdAccessor = LivingEntityAccessor.getDataHealthIdAccessor();
            target.getEntityData().set(dataHealthIdAccessor, newHealth);
            return getHealthGap(target, newHealth);
        } catch (Exception e) {
            LOGGER.debug("[TrueDamage] EntityData damage failed: {}", e.getMessage());
            return FAILED_ATTEMPT_GAP;
        }
    }

    private static float tryNBTDamage(LivingEntity target, float newHealth) {
        try {
            CompoundTag nbt = new CompoundTag();
            target.saveWithoutId(nbt);
            float currentHealth = target.getHealth();
            boolean modified = false;

            if (nbt.contains("Health")) {
                nbt.putFloat("Health", newHealth);
                modified = true;
            }
            if (modifyFloatValuesInNBT(nbt, currentHealth, newHealth)) {
                modified = true;
            }
            if (modified) {
                target.load(nbt);
            }
            return modified ? getHealthGap(target, newHealth) : FAILED_ATTEMPT_GAP;
        } catch (Exception e) {
            LOGGER.debug("[TrueDamage] NBT damage failed: {}", e.getMessage());
            return FAILED_ATTEMPT_GAP;
        }
    }

    private static boolean modifyFloatValuesInNBT(CompoundTag nbt, float originalHealth, float newHealth) {
        boolean modified = false;
        for (String key : nbt.getAllKeys()) {
            if (nbt.get(key) instanceof net.minecraft.nbt.FloatTag) {
                float value = nbt.getFloat(key);
                if (isHealthMatch(value, originalHealth)) {
                    nbt.putFloat(key, newHealth);
                    modified = true;
                }
            } else if (nbt.get(key) instanceof CompoundTag compoundTag) {
                if (modifyFloatValuesInNBT(compoundTag, originalHealth, newHealth)) {
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static boolean isHealthMatch(float value1, float value2) {
        return Math.abs(value1 - value2) < HEALTH_TOLERANCE;
    }

    private static float getHealthGap(LivingEntity target, float expectedHealth) {
        return Math.abs(target.getHealth() - expectedHealth);
    }

    private static boolean finishHealthChange(LivingEntity target, float newHealth, LivingEntity attacker) {
        if (newHealth <= 0.0f && target.getHealth() <= HEALTH_TOLERANCE) {
            handleTrueDamageDeath(target, attacker);
        }
        return true;
    }

    private static void handleTrueDamageDeath(LivingEntity target, LivingEntity attacker) {
        DamageSource damageSource = createDamageSource(target, attacker);
        target.die(damageSource);
        if (!target.isRemoved() && target.isDeadOrDying()) {
            ((LivingEntityInvoker) target).maidspell$invokeTickDeath();
        }
    }

    private static DamageSource createDamageSource(LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player) {
            return target.damageSources().playerAttack(player);
        }
        if (attacker != null) {
            return target.damageSources().mobAttack(attacker);
        }
        return target.damageSources().generic();
    }

    public static String getEntityDataInfo(LivingEntity entity) {
        try {
            EntityDataAccessor<Float> dataHealthIdAccessor = LivingEntityAccessor.getDataHealthIdAccessor();
            float health = entity.getHealth();
            float entityDataHealth = entity.getEntityData().get(dataHealthIdAccessor);

            StringBuilder sb = new StringBuilder();
            sb.append("=== EntityData Analysis for ").append(entity.getClass().getSimpleName()).append(" ===\n");
            sb.append("Current Health: ").append(health).append("\n");
            sb.append("Max Health: ").append(entity.getMaxHealth()).append("\n");
            sb.append("Health Data ID: ").append(dataHealthIdAccessor.id()).append("\n");
            sb.append("EntityData Health: ").append(entityDataHealth);
            if (isHealthMatch(entityDataHealth, health)) {
                sb.append(" <- HEALTH MATCH");
            }
            sb.append("\n");
            sb.append("\n=== NBT Health Information ===\n");
            try {
                CompoundTag nbt = new CompoundTag();
                entity.saveWithoutId(nbt);
                if (nbt.contains("Health")) {
                    sb.append("NBT Health: ").append(nbt.getFloat("Health")).append("\n");
                }
            } catch (Exception e) {
                sb.append("Failed to read NBT: ").append(e.getMessage()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Failed to access entity data: " + e.getMessage();
        }
    }
}
