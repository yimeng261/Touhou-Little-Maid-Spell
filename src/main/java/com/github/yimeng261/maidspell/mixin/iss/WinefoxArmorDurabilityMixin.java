package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxNonLethalGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents formal Winefox challenge damage from wearing down a player's armor. */
@Mixin(Inventory.class)
public abstract class WinefoxArmorDurabilityMixin {
    @Shadow @Final public Player player;

    @Inject(method = "hurtArmor", at = @At("HEAD"), cancellable = true)
    private void maidspell$preventWinefoxArmorDamage(
            DamageSource source,
            float damage,
            int[] slots,
            CallbackInfo ci) {
        if (WinefoxNonLethalGuard.isPlayerInActiveChallenge(this.player)) {
            ci.cancel();
        }
    }
}
