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
 * Legendary Monsters assumes every hit entity is a LivingEntity. Guard all
 * non-living hitboxes before the bomb reaches that cast, without depending on
 * optional mods' entity classes that may be absent in older versions.
 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.SmallAnnihilationBombEntity",
        remap = false)
@Pseudo
public abstract class SmallAnnihilationBombEntityMixin {
    @Inject(method = "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$discardOnNonLivingEntity(EntityHitResult hitResult, CallbackInfo callback) {
        if (!(hitResult.getEntity() instanceof LivingEntity)) {
            ((Entity) (Object) this).discard();
            callback.cancel();
        }
    }
}
