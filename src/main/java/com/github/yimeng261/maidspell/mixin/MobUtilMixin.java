package com.github.yimeng261.maidspell.mixin;

import com.Polarice3.Goety.utils.MobUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidGoetySpellData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin到Goety的MobUtil类
 * 扩展isSpellCasting方法以支持女仆的施法状态检测
 * 注意：使用@Pseudo注解表示这个类可能不存在（当Goety未安装时）
 */
@Pseudo
@Mixin(value = MobUtil.class, remap = false)
public class MobUtilMixin {

    /**
     * 在isSpellCasting方法开始时注入
     * 如果是女仆且正在施放Goety法术，直接返回true
     */
    @Inject(method = "isSpellCasting", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onIsSpellCasting(LivingEntity livingEntity, CallbackInfoReturnable<Boolean> cir) {
        if (livingEntity instanceof EntityMaid maid) {
            //Global.LOGGER.debug("maid: {}, isCasting:{}", maid.getDisplayName(), MaidGoetySpellData.getOrCreate(maid).isCasting());
            if(MaidGoetySpellData.getOrCreate(maid).isCasting()){
                cir.setReturnValue(true);
            }
        }
    }
}

