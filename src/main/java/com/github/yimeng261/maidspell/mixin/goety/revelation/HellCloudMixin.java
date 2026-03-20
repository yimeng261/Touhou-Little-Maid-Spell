package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.effects.GoetyEffects;
import com.Polarice3.Goety.common.entities.boss.Apostle;
import com.Polarice3.Goety.common.entities.projectiles.HellCloud;
import com.Polarice3.Goety.utils.ModDamageSource;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 晋升之环女仆的狱云伤害调整为目标最大生命值的 1%。
 */
@Mixin(HellCloud.class)
public abstract class HellCloudMixin {

    @Inject(
        method = "hurtEntities",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void maidSpell$modifyDamage(LivingEntity target, CallbackInfo ci) {
        if (target == null) {
            return;
        }

        HellCloud hellCloud = (HellCloud) (Object) this;
        if (!BaubleStateManager.hasMaidWithAscensionHalo(hellCloud.getOwner())) {
            return;
        }

        ci.cancel();

        float damage = target.getMaxHealth() * 0.01F + hellCloud.getExtraDamage();
        if (target.hurt(ModDamageSource.hellfire(hellCloud, hellCloud.getOwner()), damage)
            && hellCloud.getOwner() instanceof Apostle) {
            target.addEffect(new MobEffectInstance(GoetyEffects.BURN_HEX.get(), 1200));
        }
    }
}
