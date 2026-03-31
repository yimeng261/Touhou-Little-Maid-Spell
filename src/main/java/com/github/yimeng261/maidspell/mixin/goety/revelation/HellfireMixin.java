package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.projectiles.GroundProjectile;
import com.Polarice3.Goety.common.entities.projectiles.Hellfire;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：Hellfire 伤害增强
 * 支持终末之环和晋升之环
 * 使用 ModifyVariable 避免与 revelationfix 的 ModifyConstant 冲突
 */
@Mixin(Hellfire.class)
public abstract class HellfireMixin extends GroundProjectile {

    @Unique
    private LivingEntity maidSpell$target;

    public HellfireMixin(EntityType<? extends Entity> p_i50170_1_, Level p_i50170_2_) {
        super(p_i50170_1_, p_i50170_2_);
    }

    /**
     * 检查所有者是否为装备晋升之环的女仆
     */
    @Unique
    private boolean maidSpell$isMaidWithAscensionHalo() {
        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity livingOwner)) {
            return false;
        }
        return BaubleStateManager.hasMaidWithAscensionHalo(livingOwner);
    }


    @Inject(at = @At("HEAD"), method = "dealDamageTo", remap = false)
    private void maidSpell$getTarget(LivingEntity target, CallbackInfo ci) {
        this.maidSpell$target = target;
    }

    /**
     * 修改伤害值变量，在 revelationfix 的 ModifyConstant 之后执行
     * 这样可以覆盖 revelationfix 的逻辑，专门处理女仆的情况
     */
    @ModifyVariable(method = "dealDamageTo", at = @At(value = "STORE", ordinal = 0), remap = false, name = "damage")
    private float maidSpell$modifyDamage(float damage) {
        if (maidSpell$target != null) {
            // 晋升之环：伤害为目标最大生命值的 4.44%
            if (maidSpell$isMaidWithAscensionHalo()) {
                return maidSpell$target.getMaxHealth() * 0.0444F;
            }
        }
        return damage;
    }
}
