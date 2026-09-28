package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.api.IBossDamageClamp;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 在原版伤害链中读取权威生命，并为绕过 hurt 的路径补上单次伤害上限。
 * require=0 避免上游局部变量结构改变时阻止启动。
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

    @ModifyVariable(
        method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0,
        require = 0
    )
    private float maidspell$clampFinalDamage(float finalDamage) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof IBossDamageClamp clamp)) {
            return finalDamage;
        }
        // 0 是"这一击完全被吸收/取消"，原版靠它跳过整段写血逻辑，不能被抬起来。
        // 非有限值同样原样放行，交给原本的 fcmpl 分支处理，避免把 NaN 变成合法伤害。
        if (finalDamage <= 0.0F || !Float.isFinite(finalDamage)) {
            return finalDamage;
        }
        float max = clamp.maidspell$maxDamagePerHit(finalDamage);
        if (max == IBossDamageClamp.NO_DAMAGE_CAP || max >= finalDamage) {
            return finalDamage;
        }
        return Math.max(max, 0.0F);
    }
}
