package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 星之魔女对外的 getHealth 一律返回权威生命。
 * 其他模组即使改写了她的 getHealth，返回前这里也会把值换回权威生命。
 */
@Mixin(value = MagicalWinefoxBossEntity.class, remap = false, priority = 2000)
public abstract class MagicalWinefoxBossHealthMixin {

    @ModifyReturnValue(method = "getHealth()F", at = @At("RETURN"))
    private float maidspell$returnAuthoritativeHealth(float original) {
        return ((MagicalWinefoxBossEntity) (Object) this).maidspell$authoritativeHealth();
    }
}
