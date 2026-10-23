package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import io.redspace.ironsspellbooks.player.ServerPlayerEvents;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerPlayerEvents.class, remap = false)
public abstract class ServerPlayerInvisibilityTargetMixin {
    // 旧版铁魔法没有真隐身的索敌否决事件，所以这个重定向是可选的。
    @Redirect(method = "onLivingChangeTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z",
            remap = true), require = 0)
    private static boolean maidspell$allowVisibleWinefoxTarget(LivingEntity target, Holder<MobEffect> effect,
                                                               LivingChangeTargetEvent event) {
        if (effect.is(MobEffectRegistry.TRUE_INVISIBILITY.getId())
                && event.getEntity() instanceof MagicalWinefoxBossEntity boss
                && boss.canDiscoverInvisibleTarget(target)) {
            return false;
        }
        return target.hasEffect(effect);
    }
}
