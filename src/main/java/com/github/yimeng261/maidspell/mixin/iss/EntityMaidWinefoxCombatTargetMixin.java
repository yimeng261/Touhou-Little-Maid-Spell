package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 把遭遇战的目标过滤作用于所有女仆攻击任务，包括枪械和拔刀剑。 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidWinefoxCombatTargetMixin {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At("HEAD"), cancellable = true, remap = true)
    private void maidspell$excludeInactiveWinefox(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof MagicalWinefoxBossEntity boss && (!boss.isBattleActive() || boss.isRemoved())) {
            cir.setReturnValue(false);
        }
    }
}
