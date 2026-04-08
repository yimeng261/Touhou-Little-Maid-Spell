package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.mixin.LivingEntityInvoker;
import com.github.yimeng261.maidspell.mixin.SynchedEntityDataMixin;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrueDamageUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float HEALTH_TOLERANCE = 0.01f;
    private static final float FAILED_ATTEMPT_GAP = Float.POSITIVE_INFINITY;

    private static final Map<String, List<Integer>> healthIdMap = new HashMap<>();

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

        float originalHealth = target.getHealth();
        float healthGap = FAILED_ATTEMPT_GAP;

        if (newHealth < originalHealth) {
            healthGap = tryActuallyHurtDamage(target, originalHealth, newHealth, attacker);
            if (healthGap <= HEALTH_TOLERANCE) {
                return finishHealthChange(target, newHealth, attacker);
            }
        }

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

    private static float tryActuallyHurtDamage(LivingEntity target, float originalHealth, float newHealth, LivingEntity attacker) {
        float damage = originalHealth - newHealth;
        if (damage <= 0.0f) {
            return getHealthGap(target, newHealth);
        }

        try {
            DamageSource damageSource = createDamageSource(target, attacker);
            ((LivingEntityInvoker) target).maidspell$invokeActuallyHurt(damageSource, damage);
            return getHealthGap(target, newHealth);
        } catch (Exception e) {
            LOGGER.debug("[TrueDamage] actuallyHurt damage failed: {}", e.getMessage());
            return FAILED_ATTEMPT_GAP;
        }
    }

    private static float tryEntityDataDamage(LivingEntity target, float newHealth) {
        try {
            SynchedEntityDataMixin dataMixin = (SynchedEntityDataMixin) target.getEntityData();
            Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById = dataMixin.getItemsById();
            List<Integer> healthIds = findAllHealthIds(target, itemsById);
            if (healthIds.isEmpty()) {
                return FAILED_ATTEMPT_GAP;
            }

            boolean modified = false;
            for (Integer healthId : healthIds) {
                try {
                    @SuppressWarnings({"unchecked", "deprecation"})
                    SynchedEntityData.DataItem<Float> dataItem = (SynchedEntityData.DataItem<Float>) itemsById.get(healthId);
                    target.getEntityData().set(dataItem.getAccessor(), newHealth);
                    modified = true;
                } catch (Exception e) {
                    LOGGER.debug("[TrueDamage] Failed to modify EntityData ID {}: {}", healthId, e.getMessage());
                }
            }
            return modified ? getHealthGap(target, newHealth) : FAILED_ATTEMPT_GAP;
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

    private static List<Integer> findAllHealthIds(LivingEntity target, Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById) {
        String className = target.getClass().getSimpleName();
        float currentHealth = target.getHealth();

        List<Integer> cachedIds = healthIdMap.get(className);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            List<Integer> validIds = new ArrayList<>();
            for (Integer id : cachedIds) {
                try {
                    @SuppressWarnings("deprecation")
                    SynchedEntityData.DataItem<?> item = itemsById.get(id);
                    if (item != null && item.getValue() instanceof Float) {
                        validIds.add(id);
                    }
                } catch (Exception ignored) {
                }
            }
            if (!validIds.isEmpty()) {
                return validIds;
            }
        }

        List<Integer> healthIds = new ArrayList<>();
        itemsById.forEach((id, dataItem) -> {
            if (dataItem.getValue() instanceof Float floatValue) {
                if (isHealthMatch(floatValue, currentHealth) || (floatValue > 0 && floatValue <= target.getMaxHealth())) {
                    healthIds.add(id);
                }
            }
        });
        healthIdMap.put(className, healthIds);
        return healthIds;
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
            SynchedEntityDataMixin dataMixin = (SynchedEntityDataMixin) entity.getEntityData();
            Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById = dataMixin.getItemsById();
            float health = entity.getHealth();
            String className = entity.getClass().getSimpleName();

            StringBuilder sb = new StringBuilder();
            sb.append("=== EntityData Analysis for ").append(className).append(" ===\n");
            sb.append("Current Health: ").append(health).append("\n");
            sb.append("Max Health: ").append(entity.getMaxHealth()).append("\n");
            List<Integer> healthIds = findAllHealthIds(entity, itemsById);
            if (!healthIds.isEmpty()) {
                sb.append("Found Health IDs: ").append(healthIds).append("\n");
            }
            sb.append("\n=== All EntityData Float Values ===\n");
            itemsById.forEach((id, dataItem) -> {
                if (dataItem.getValue() instanceof Float floatValue) {
                    sb.append("  ID ").append(id).append(": ").append(floatValue);
                    if (isHealthMatch(floatValue, health)) {
                        sb.append(" <- HEALTH MATCH");
                    } else if (floatValue > 0 && floatValue <= entity.getMaxHealth()) {
                        sb.append(" <- Possible Health");
                    }
                    sb.append("\n");
                }
            });
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
