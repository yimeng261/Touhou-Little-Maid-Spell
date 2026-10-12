package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 发簪转伤挂在女仆 actuallyHurt 开头，与盾牌、受击间隔判定之后的结算点一致。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidHairpinMixin extends TamableAnimal {
    protected EntityMaidHairpinMixin(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void maidspell$redirectHairpinDamage(DamageSource source, float amount, CallbackInfo ci) {
        if (this.isInvulnerableTo(source) || this.damageContainers.isEmpty()) {
            return;
        }
        HairpinBauble.redirectToOwner((EntityMaid) (Object) this, source, this.damageContainers.peek());
    }
}
