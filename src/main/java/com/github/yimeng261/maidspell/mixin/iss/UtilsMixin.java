package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * 使女仆施法时设置了目标实体后，不再被视线射线检测覆盖目标
 */
@Mixin(value = Utils.class, remap = false)
public class UtilsMixin {
    @Inject(method = "preCastTargetHelper(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/magic/MagicData;Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;IFZLjava/util/function/Predicate;)Z", at = @At("HEAD"), cancellable = true)
    private static void beforePreCastTargetHelper(Level level, LivingEntity caster, MagicData playerMagicData, AbstractSpell spell, int range, float aimAssist, boolean sendFailureMessage, Predicate<LivingEntity> filter, CallbackInfoReturnable<Boolean> cir) {
        // 只处理女仆的目标实体
        if (!(caster instanceof EntityMaid))
            return;
        // 如果没有指定目标实体则还是使用默认的射线检测
        if (!(playerMagicData.getAdditionalCastData() instanceof TargetEntityCastData targetEntityCastData))
            return;
        
        LivingEntity targetEntity = targetEntityCastData.getTarget((ServerLevel) caster.level());
        // 如果目标实体是玩家则发送提示
        if (targetEntity instanceof ServerPlayer targetPlayerEntity) {
            Utils.sendTargetedNotification(targetPlayerEntity, caster, spell);
        }
        cir.setReturnValue(true);
    }
}
