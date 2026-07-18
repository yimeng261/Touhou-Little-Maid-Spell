package com.github.yimeng261.maidspell.mixin.usefulmagic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.coostack.usefulmagic.utils.FriendFilterHelper;

/**
 * 给 UsefulMagic 的友军判定补上女仆分支。
 *
 * <p>原版 {@code FriendFilterHelper.filterFriend/filterNotFriend} 开头即 {@code if(!(source instanceof Player))}，
 * 非玩家施法者一律「谁都不是朋友 / 谁都是敌人」，导致女仆的 AOE/领域/自动索敌法术会误伤主人、同队女仆、结盟单位。
 * 这里在 source 为女仆时改走本模组现成的 {@link MaidSpellAllyResolver#areFriendly}（记分板 Team 结盟 + owner 亲和），
 * 复原友伤保护；非女仆 source 不拦截，保持原版行为。
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-07-17
 */
@Mixin(value = FriendFilterHelper.class, remap = false)
public class FriendFilterHelperMixin {

    @Inject(
            method = "filterFriend(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void maidspell$filterFriendForMaid(LivingEntity source, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (source instanceof EntityMaid) {
            cir.setReturnValue(MaidSpellAllyResolver.areFriendly(source, target));
        }
    }

    @Inject(
            method = "filterNotFriend(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void maidspell$filterNotFriendForMaid(LivingEntity source, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (source instanceof EntityMaid) {
            cir.setReturnValue(!MaidSpellAllyResolver.areFriendly(source, target));
        }
    }
}
