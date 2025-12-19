package com.github.yimeng261.maidspell.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import com.github.yimeng261.maidspell.MaidSpellMod;

/**
 * 自定义伤害源类
 * 用于创建带有额外信息的伤害类型
 */
public class InfoDamageSource extends DamageSource {
    public String msg_type;
    public DamageSource damage_source;
    public LivingEntity sourceEntity;
    
    // 定义自定义伤害类型的ResourceKey
    @SuppressWarnings("removal")
    public static final ResourceKey<DamageType> INFO_DAMAGE = ResourceKey.create(
        Registries.DAMAGE_TYPE, 
        new ResourceLocation(MaidSpellMod.MOD_ID, "info_damage")
    );

    public void setSourceEntity(LivingEntity sourceEntity) {
        this.sourceEntity = sourceEntity;
    }

    public InfoDamageSource(String msg_type, DamageSource damage_source) {
        super(damage_source.typeHolder(), damage_source.getEntity(), damage_source.getDirectEntity(), damage_source.getSourcePosition());
        this.msg_type = msg_type;
        this.damage_source = damage_source;
    }

    
    public InfoDamageSource(String msg_type, DamageSource damage_source, Holder<DamageType> holder) {
        super(holder, damage_source.getEntity(), damage_source.getDirectEntity(), damage_source.getSourcePosition());
        this.msg_type = msg_type;
        this.damage_source = damage_source;
    }


    public InfoDamageSource(DamageSource damage_source) {
        super(damage_source.typeHolder(), damage_source.getEntity(), damage_source.getDirectEntity(), damage_source.getSourcePosition());
        this.msg_type = "";
        this.damage_source = damage_source;
    }

    /**
     * 创建一个基于现有伤害源的InfoDamageSource，使用Level来获取正确的伤害类型注册表
     * @param level 世界实例，用于获取注册表
     * @param msg_type 消息类型
     * @param baseDamageSource 基础伤害源
     */
    public static InfoDamageSource create(Level level, String msg_type, DamageSource baseDamageSource) {
        // 安全检查
        if (level == null || baseDamageSource == null) {
            throw new IllegalArgumentException("Level and baseDamageSource cannot be null");
        }
        
        try {
            // 尝试从注册表获取伤害类型
            var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            var damageTypeHolder = registry.getHolderOrThrow(INFO_DAMAGE);
            return new InfoDamageSource(msg_type, baseDamageSource, damageTypeHolder);
        } catch (Exception e) {
            // 如果获取失败，使用原始伤害源的类型，这样可以保证网络同步的一致性
            return new InfoDamageSource(msg_type, baseDamageSource);
        }
    }

    /**
     * 不推荐使用的无参构造函数，仅用于向后兼容
     * @deprecated 使用 create(Level, String, DamageSource) 代替
     */
    @Deprecated
    public InfoDamageSource() {
        // 使用一个安全的默认伤害类型，避免网络同步问题
        super(Holder.direct(new DamageType("info_damage", 0.0f)), null, null, null);
        this.msg_type = "";
        this.damage_source = null;
    }

}
