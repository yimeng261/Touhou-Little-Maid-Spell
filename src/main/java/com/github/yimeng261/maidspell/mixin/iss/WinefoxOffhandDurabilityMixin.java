package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxNonLethalGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** 正式挑战中玩家副手物品（盾牌等）不损耗耐久。 */
@Mixin(ItemStack.class)
public abstract class WinefoxOffhandDurabilityMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void maidspell$preventWinefoxOffhandDamage(
            int amount, ServerLevel level, LivingEntity entity, Consumer<Item> onBroken, CallbackInfo ci) {
        if (amount > 0 && entity instanceof Player player
                && player.getOffhandItem() == (Object) this
                && WinefoxNonLethalGuard.isPlayerInActiveChallenge(player)) {
            ci.cancel();
        }
    }
}
