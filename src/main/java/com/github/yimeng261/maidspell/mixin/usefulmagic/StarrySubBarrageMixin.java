package com.github.yimeng261.maidspell.mixin.usefulmagic;

import cn.coostack.usefulmagic.meteorite.starry.StarrySubBarrage;
import com.github.yimeng261.maidspell.compat.usefulmagic.UsefulMagicTerrainProtection;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 星陨爆炸保留实体伤害，仅关闭女仆来源的地形交互。 */
@Mixin(value = StarrySubBarrage.class, remap = false)
public abstract class StarrySubBarrageMixin {
    @ModifyExpressionValue(method = "triggerImpactExplosion", at = @At(value = "INVOKE",
            target = "Lcn/coostack/usefulmagic/gamerules/UsefulMagicGameRules;canDestroyTerrain(Lnet/minecraft/world/level/Level;)Z"))
    private boolean maidspell$protectTerrain(boolean original) {
        return original && !UsefulMagicTerrainProtection.blocksTerrainDamage(
                ((StarrySubBarrage) (Object) this).getShooter());
    }
}
