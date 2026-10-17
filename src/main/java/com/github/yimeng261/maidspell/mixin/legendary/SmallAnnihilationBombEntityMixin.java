package com.github.yimeng261.maidspell.mixin.legendary;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 传奇怪物（Legendary Monsters）的小型湮灭弹会把所有命中的实体强制转换为 LivingEntity。
 * 在转换之前拦截非生物实体的命中并销毁弹体，目标类通过名称引用，不依赖可选模组的类。
 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.SmallAnnihilationBombEntity",
        remap = false)
@Pseudo
public abstract class SmallAnnihilationBombEntityMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$discardOnNonLivingEntity(EntityHitResult hitResult, CallbackInfo callback) {
        if (!(hitResult.getEntity() instanceof LivingEntity)) {
            ((Entity) (Object) this).discard();
            callback.cancel();
        }
    }
}
