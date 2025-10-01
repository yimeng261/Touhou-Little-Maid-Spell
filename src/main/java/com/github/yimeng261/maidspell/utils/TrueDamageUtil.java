package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.mixin.SynchedEntityDataMixin;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

/**
 * 真实伤害工具类
 * 通过直接修改entityData来实现真实伤害，而不是使用setHealth方法
 */
public class TrueDamageUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String,Integer> healthIdMap = new HashMap<>();

    /**
     * 对目标实体造成真实伤害
     * @param target 目标实体
     * @param damage 伤害值
     */
    public static void dealTrueDamage(LivingEntity target, float damage) {
        try {
            // 获取当前健康值
            SynchedEntityDataMixin dataMixin = (SynchedEntityDataMixin) target.getEntityData();
            Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById = dataMixin.getItemsById();
            String className = target.getClass().getSimpleName();
            try{
                
                getEntityDataInfo(target);
                @SuppressWarnings("unchecked")
                SynchedEntityData.DataItem<Float> dataItem = (SynchedEntityData.DataItem<Float>) itemsById.get(healthIdMap.get(className));
                float finalhealth = Math.max(0.0f, dataItem.getValue() - damage);
                target.getEntityData().set(dataItem.getAccessor(), finalhealth);
                target.getEntityData().isDirty();
            }catch(Exception e){
                LOGGER.error("[TrueDamage] Failed to deal true damage to entity", e);
            }
            
            
        } catch (Exception e) {
            LOGGER.error("[TrueDamage] Failed to deal true damage to entity", e);
            // 如果直接修改失败，回退到setHealth方法
            target.setHealth(Math.max(0.0f, target.getHealth() - damage));
        }
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
            int healthId = healthIdMap.getOrDefault(className, -1);

            if(healthId != -1 && itemsById.get(healthId).getValue() instanceof Float v && v == health){
                return "healthId: " + healthId + " health: " + health;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("EntityData for ").append(entity.getClass().getSimpleName()).append(":\n");
            

            // 显示所有数据项
            itemsById.forEach((id, dataItem) -> {
                if (dataItem.getValue() instanceof Float v && v == health) {
                    healthIdMap.put(entity.getClass().getSimpleName(), id);
                    sb.append("  Health -> ");
                    sb.append("  ID ").append(id).append(": ");
                    sb.append(dataItem.getValue()).append("\n");
                }
            });
            
            return sb.toString();
        } catch (Exception e) {
            return "Failed to access entity data: " + e.getMessage();
        }
    }
}
