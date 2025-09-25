package com.github.yimeng261.maidspell.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
        super(damage_source.typeHolder(),damage_source.getEntity(), damage_source.getDirectEntity(), damage_source.getSourcePosition());
    }

}
