package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.mixin.LivingEntityInvoker;
import com.github.yimeng261.maidspell.mixin.SynchedEntityDataMixin;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

/**
 * 真实伤害工具类
 * 通过同时修改entityData和NBT来实现真实伤害，确保对所有实体类型都有效
 */
public class TrueDamageUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float HEALTH_TOLERANCE = 0.01f;
    private static final float FAILED_ATTEMPT_GAP = Float.POSITIVE_INFINITY;
    
    private static final Map<String, List<Integer>> healthIdMap = new HashMap<>();

    /**
     * 对目标实体造成真实伤害
     *
     * @param target 目标实体
     * @param damage 伤害值
     * @param attacker 攻击者
     * @return 是否成功
     */
    public static boolean dealTrueDamage(LivingEntity target, float damage, LivingEntity attacker) {
        if(target == null){
            return false;
        }
        float newHealth = Math.max(0.0f, target.getHealth() - damage);
        return setNewHealth(target, newHealth, attacker);
    }

    /**
     * 设置目标实体的新血量
     *
     * @param target 目标实体
     * @param newHealth 新血量
     * @param attacker 攻击者
     * @return 是否成功
     */
    public static boolean setNewHealth(LivingEntity target, float newHealth, LivingEntity attacker) {
        if(target == null){
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

        //LOGGER.debug("[TrueDamage] NewHealth {} applied: {} -> {} (gap: {})", newHealth, originalHealth, target.getHealth(), healthGap);
        return false;
    }

    /**
     * 尝试通过EntityData造成伤害。
     * 返回预期血量与当前血量的差距；失败返回正无穷。
     */
    private static float tryEntityDataDamage(LivingEntity target, float newHealth) {
        try {
            SynchedEntityDataMixin dataMixin = (SynchedEntityDataMixin) target.getEntityData();
            Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById = dataMixin.getItemsById();
            
            // 获取所有可能的health ID
            List<Integer> healthIds = findAllHealthIds(target, itemsById);
            
            if (healthIds.isEmpty()) {
                return FAILED_ATTEMPT_GAP;
            }
            
            // 修改所有匹配的EntityData项
            boolean modified = false;
            for (Integer healthId : healthIds) {
                try {
                    @SuppressWarnings({ "unchecked", "deprecation" })
                    SynchedEntityData.DataItem<Float> dataItem = 
                        (SynchedEntityData.DataItem<Float>) itemsById.get(healthId);
                    target.getEntityData().set(dataItem.getAccessor(), newHealth);
                    modified = true;
                    //LOGGER.debug("[TrueDamage] Modified EntityData ID {}: {}", healthId, newHealth);
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
    
    /**
     * 尝试通过NBT造成伤害。
     * 返回预期血量与当前血量的差距；失败返回正无穷。
     */
    private static float tryNBTDamage(LivingEntity target, float newHealth) {
        try {
            // 获取实体的NBT数据
            CompoundTag nbt = new CompoundTag();
            target.saveWithoutId(nbt);
            float currentHealth = target.getHealth();
            
            boolean modified = false;
            
            // 修改直接的Health字段
            if (nbt.contains("Health")) {
                nbt.putFloat("Health", newHealth);
                modified = true;
                //LOGGER.debug("[TrueDamage] Modified NBT Health: {}", newHealth);
            }
            
            
            // 递归搜索并修改所有匹配的Float值
            if (modifyFloatValuesInNBT(nbt, currentHealth, newHealth)) {
                modified = true;
            }
            
            // 将修改后的NBT数据加载回实体
            if (modified) {
                target.load(nbt);
            }
            
            return modified ? getHealthGap(target, newHealth) : FAILED_ATTEMPT_GAP;
            
        } catch (Exception e) {
            LOGGER.debug("[TrueDamage] NBT damage failed: {}", e.getMessage());
            return FAILED_ATTEMPT_GAP;
        }
    }
    
    /**
     * 递归修改NBT中所有匹配的Float值
     */
    private static boolean modifyFloatValuesInNBT(CompoundTag nbt, float originalHealth, float newHealth) {
        boolean modified = false;
        
        for (String key : nbt.getAllKeys()) {
            if (nbt.get(key) instanceof net.minecraft.nbt.FloatTag) {
                float value = nbt.getFloat(key);
                if (isHealthMatch(value, originalHealth)) {
                    nbt.putFloat(key, newHealth);
                    modified = true;
                    //LOGGER.debug("[TrueDamage] Modified NBT Float {}: {}", key, newHealth);
                }
            } else if (nbt.get(key) instanceof CompoundTag) {
                if (modifyFloatValuesInNBT(nbt.getCompound(key), originalHealth, newHealth)) {
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    /**
     * 检查两个血量值是否匹配
     */
    private static boolean isHealthMatch(float value1, float value2) {
        return Math.abs(value1 - value2) < HEALTH_TOLERANCE;
    }

    private static float getHealthGap(LivingEntity target, float expectedHealth) {
        return Math.abs(target.getHealth() - expectedHealth);
    }
    
    /**
     * 查找所有可能的health ID
     */
    private static List<Integer> findAllHealthIds(LivingEntity target, Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById) {
        String className = target.getClass().getSimpleName();
        float currentHealth = target.getHealth();
        
        // 先尝试使用缓存的ID
        List<Integer> cachedIds = healthIdMap.get(className);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            // 验证缓存的ID是否仍然有效
            List<Integer> validIds = new ArrayList<>();
            for (Integer id : cachedIds) {
                try {
                    @SuppressWarnings("deprecation")
                    SynchedEntityData.DataItem<?> item = itemsById.get(id);
                    if (item != null && item.getValue() instanceof Float) {
                        validIds.add(id);
                    }
                } catch (Exception e) {
                    // ID无效，忽略
                }
            }
            if (!validIds.isEmpty()) {
                return validIds;
            }
        }
        
        // 重新搜索所有可能的health ID
        List<Integer> healthIds = new ArrayList<>();
        itemsById.forEach((id, dataItem) -> {
            if (dataItem.getValue() instanceof Float floatValue) {
                // 匹配当前血量值或接近的值
                if (isHealthMatch(floatValue, currentHealth) || 
                    (floatValue > 0 && floatValue <= target.getMaxHealth())) {
                    healthIds.add(id);
                }
            }
        });
        
        // 缓存结果
        healthIdMap.put(className, healthIds);
        return healthIds;
    }

    private static void handleTrueDamageDeath(LivingEntity target, LivingEntity attacker) {
        DamageSource damageSource = createDamageSource(target, attacker);
        target.die(damageSource);

        if (!target.isRemoved() && target.isDeadOrDying()) {
            ((LivingEntityInvoker) target).maidspell$invokeTickDeath();
        }
    }

    private static boolean finishHealthChange(LivingEntity target, float newHealth, LivingEntity attacker) {
        if (newHealth <= 0.0f && target.getHealth() <= HEALTH_TOLERANCE) {
            handleTrueDamageDeath(target, attacker);
        }
        return true;
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



    /**
     * 获取实体的entityData详细信息
     * @param entity 目标实体
     * @return entityData的内容描述
     */
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
            
            // 获取所有可能的health ID
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
            
            // 显示NBT信息
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
