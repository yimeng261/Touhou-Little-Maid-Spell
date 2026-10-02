package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.effect.ThunderstormEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

/**
 * 在 ISS 雷暴落雷前，从候选名单中排除施法者一方的女仆、宠物和召唤物
 */
@Mixin(value = ThunderstormEffect.class, remap = false)
public abstract class ThunderstormEffectMixin {
    /** 复用 ISS 查询再过滤；签名须匹配擦除泛型后的调用描述符 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(
            method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                    remap = true
            ),
            remap = true
    )
    private List maidspell$excludeCasterAlliesFromThunderstorm(Level level, Class entityClass, AABB area, Predicate predicate,
                                                               Operation<List> original,
                                                               @Local(argsOnly = true) LivingEntity caster) {
        return original.call(level, entityClass, area,
                predicate.and(MaidSpellAllyResolver.friendlyTo(caster).negate()));
    }
}
