package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.effect.ThunderstormEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

/**
 * 在 ISS 雷暴落雷前，从候选名单中排除女仆
 */
@Mixin(value = ThunderstormEffect.class, remap = false)
public abstract class ThunderstormEffectMixin {
    /** 复用 ISS 查询并过滤女仆；签名须匹配擦除泛型后的调用描述符 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                    remap = true
            ),
            remap = true
    )
    private List maidspell$excludeMaidsFromThunderstorm(Level level, Class entityClass, AABB area, Predicate predicate) {
        List targets = level.getEntitiesOfClass(entityClass, area, predicate);
        if (!targets.isEmpty() && targets.stream().anyMatch(EntityMaid.class::isInstance)) {
            targets.removeIf(EntityMaid.class::isInstance);
        }
        return targets;
    }
}
