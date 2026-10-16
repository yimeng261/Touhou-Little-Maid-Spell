package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import io.redspace.ironsspellbooks.player.ServerPlayerEvents;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerPlayerEvents.class, remap = false)
public abstract class ServerPlayerInvisibilityTargetMixin {
    // Older ISS builds have no true-invisibility event veto, so this redirect is optional.
    @Redirect(method = "onLivingChangeTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z",
            remap = true), require = 0)
    private static boolean maidspell$allowVisibleWinefoxTarget(LivingEntity target, MobEffect effect,
                                                               LivingChangeTargetEvent event) {
        if (effect == MobEffectRegistry.TRUE_INVISIBILITY.get()
                && event.getEntity() instanceof MagicalWinefoxBossEntity boss
                && boss.canDiscoverInvisibleTarget(target)) {
            return false;
        }
        return target.hasEffect(effect);
    }
}
