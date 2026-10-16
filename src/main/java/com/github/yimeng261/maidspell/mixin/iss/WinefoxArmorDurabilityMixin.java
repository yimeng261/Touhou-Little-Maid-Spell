package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxNonLethalGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 正式挑战中星之魔女造成的伤害不磨损玩家护甲，头盔单独受击的路径一并拦下。 */
@Mixin(Player.class)
public abstract class WinefoxArmorDurabilityMixin {
    @Inject(method = {"hurtArmor", "hurtHelmet"}, at = @At("HEAD"), cancellable = true)
    private void maidspell$preventWinefoxArmorDamage(DamageSource source, float damage, CallbackInfo ci) {
        if (WinefoxNonLethalGuard.isPlayerInActiveChallenge((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
