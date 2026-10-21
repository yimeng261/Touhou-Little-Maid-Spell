package com.github.yimeng261.maidspell.mixin.usefulmagic;

import cn.coostack.usefulmagic.utils.ExplosionUtil;
import com.github.yimeng261.maidspell.compat.usefulmagic.UsefulMagicTerrainProtection;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 实用魔法的通用爆破同时遵循自身游戏规则与女仆地形保护。 */
@Mixin(value = ExplosionUtil.class, remap = false)
public abstract class ExplosionUtilMixin {
    @ModifyExpressionValue(
            method = {"createRoundExplosion", "createSolidBallExplosion", "createHollowSphereExplosion",
                    "createHollowSphereExplosionIgnoreWater"},
            at = @At(value = "INVOKE",
                    target = "Lcn/coostack/usefulmagic/gamerules/UsefulMagicGameRules;canDestroyTerrain(Lnet/minecraft/world/level/Level;)Z"))
    private boolean maidspell$protectTerrain(boolean original, @Local(argsOnly = true) LivingEntity source) {
        return original && !UsefulMagicTerrainProtection.blocksTerrainDamage(source);
    }
}
