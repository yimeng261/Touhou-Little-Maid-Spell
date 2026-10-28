package com.github.yimeng261.maidspell.mixin.goety;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidGoetySpellData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.Polarice3.Goety.utils.WandUtil;

/**
 * Mixin到Goety的WandUtil类
 * 扩展findWand/findFocus方法以支持女仆的currentStaff/currentFocus读取
 * 当生物类型为女仆时，从MaidGoetySpellData读取并返回currentFocus
 * 注意：使用@Pseudo注解表示这个类可能不存在（当Goety未安装时）
 */
@Pseudo
@Mixin(value = WandUtil.class)
public class WandUtilMixin {

    /**
     * 女仆施法期间，findWand 返回 MaidGoetySpellData 里本次施法选中的法杖
     */
    @Inject(method = "findWand", at = @At("HEAD"), cancellable = true)
    private static void onFindWand(LivingEntity livingEntity, CallbackInfoReturnable<ItemStack> cir) {
        if (livingEntity instanceof EntityMaid maid) {
            ItemStack currentStaff = MaidGoetySpellData.getOrCreate(maid).getCurrentStaff();
            if (currentStaff != null && !currentStaff.isEmpty()
                    && currentStaff.getItem() instanceof IWand) {
                cir.setReturnValue(currentStaff);
            }
        }
    }

    /**
     * 在findFocus方法开始时注入
     * 如果是女仆且MaidGoetySpellData中有currentFocus，直接返回它
     */
    @Inject(method = "findFocus", at = @At("HEAD"), cancellable = true)
    private static void onFindFocus(LivingEntity livingEntity, CallbackInfoReturnable<ItemStack> cir) {
        if (livingEntity instanceof EntityMaid maid) {
            MaidGoetySpellData spellData = MaidGoetySpellData.getOrCreate(maid);
            ItemStack currentFocus = spellData.getCurrentFocus();
            if (currentFocus != null && !currentFocus.isEmpty()) {
                cir.setReturnValue(currentFocus);
            }
        }
    }
}

