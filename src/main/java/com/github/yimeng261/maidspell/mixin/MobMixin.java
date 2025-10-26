package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Mob类的Mixin，用于阻止女仆被转换成其他实体
 * 针对convertTo方法进行注入，当检测到是女仆实体时取消转换操作
 */
@Mixin(Mob.class)
public class MobMixin {
    
    /**
     * 拦截convertTo方法，阻止女仆被转换成其他实体
     * 
     * @param entityType 目标实体类型
     * @param bl 是否保留装备
     * @param cir 回调信息返回值
     */
    @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;", 
            at = @At("HEAD"), 
            cancellable = true)
    public <T extends Mob> void preventMaidConversion(EntityType<T> entityType, boolean bl, CallbackInfoReturnable<T> cir) {
        // 检查当前实体是否为女仆
        if ((Object) this instanceof EntityMaid maid) {
            Global.LOGGER.debug("阻止女仆 {} 被转换成 {}",
                maid.getUUID(), entityType.getDescriptionId());

            // 取消转换操作，返回null
            cir.setReturnValue(null);
            return;
        }
    }

}
