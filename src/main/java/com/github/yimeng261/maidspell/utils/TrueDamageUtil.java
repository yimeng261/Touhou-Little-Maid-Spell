package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.mixin.LivingEntityAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

/**
 * 真实伤害工具类
 * 通过直接修改entityData来实现真实伤害，而不是使用setHealth方法
 */
public class TrueDamageUtil {
    /**
     * 对目标实体造成真实伤害
     * @param target 目标实体
     * @param damage 伤害值
     */
    public static void dealTrueDamage(LivingEntity target, float damage) {
        SynchedEntityData entityData = target.getEntityData();
        LivingEntityAccessor livingEntityAccessor = (LivingEntityAccessor) target;
        EntityDataAccessor<Float> dataHealthIdAccessor = livingEntityAccessor.getDataHealthIdAccessor();
        Float currentHealth = entityData.get(dataHealthIdAccessor);
        entityData.set(dataHealthIdAccessor, Math.max(0.0f, currentHealth - damage));
    }



    /**
     * 获取实体的entityData详细信息
     * @param entity 目标实体
     * @return entityData的内容描述
     */
    public static String getEntityDataInfo(LivingEntity entity) {
        try {
            SynchedEntityData entityData = entity.getEntityData();
            LivingEntityAccessor livingEntityAccessor = (LivingEntityAccessor) entity;
            EntityDataAccessor<Float> dataHealthIdAccessor = livingEntityAccessor.getDataHealthIdAccessor();
            Float currentHealth = entityData.get(dataHealthIdAccessor);
            float health = entity.getHealth();
            String className = entity.getClass().getSimpleName();
            return "EntityData for " + className + ":\n" +
                    "  Health -> " + health + "\n" +
                    "  ID " + dataHealthIdAccessor.id() + ": " + currentHealth + "\n";
        } catch (Exception e) {
            return "Failed to access entity data: " + e.getMessage();
        }
    }
}
