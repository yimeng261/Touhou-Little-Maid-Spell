package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.api.IBossDamageClamp;
import com.github.yimeng261.maidspell.utils.DamageAbsorption;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在原版伤害链中读取权威生命，并为绕过 hurt 的路径补上单次伤害上限。
 */
@Mixin(LivingEntity.class)
public abstract class WinefoxDamageClampMixin {

    @WrapOperation(
        method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHealth()F")
    )
    private float maidspell$readAuthoritativeHealthForDamage(LivingEntity entity, Operation<Float> original) {
        return entity instanceof IAuthoritativeHealth authority
            ? authority.maidspell$authoritativeHealth() : original.call(entity);
    }

    @WrapOperation(
        method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDeadOrDying()Z")
    )
    private boolean maidspell$preserveCustomDefeatState(LivingEntity entity, Operation<Boolean> original) {
        return entity instanceof IAuthoritativeHealth ? false : original.call(entity);
    }

    /**
     * LivingDamageEvent.Pre 之后、吸收结算之前裁剪伤害：上限作用于扣除吸收后真正写进血条的部分，
     * 因此把吸收量加回容器，让随后的吸收结算照常扣掉它。
     */
    @WrapOperation(
        method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDamagePre(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)F")
    )
    private float maidspell$clampFinalDamage(LivingEntity entity, DamageContainer container, Operation<Float> original) {
        float damage = original.call(entity, container);
        if (!(entity instanceof IBossDamageClamp clamp)) {
            return damage;
        }
        float absorbed = DamageAbsorption.absorbedPart(entity, damage);
        float finalDamage = damage - absorbed;
        // 0 是"这一击完全被吸收/取消"，不能被抬起来；非有限值原样放行，避免把 NaN 变成合法伤害。
        if (finalDamage <= 0.0F || !Float.isFinite(finalDamage)) {
            return damage;
        }
        float max = clamp.maidspell$maxDamagePerHit(finalDamage);
        if (max == IBossDamageClamp.NO_DAMAGE_CAP || max >= finalDamage) {
            return damage;
        }
        float clamped = Math.max(max, 0.0F) + absorbed;
        container.setNewDamage(clamped);
        return clamped;
    }
}
