package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.Global;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 MobEffect.addAttributeModifiers，阻止被过滤效果的属性修改器被应用。
 *
 * <p>与 LivingEntityMixin 的 @Redirect Map.put 共同构成双重防护：
 * <ul>
 *   <li>@Redirect put → 效果不写入 activeEffects，不会 tick/显示</li>
 *   <li>本注入 → 即使效果绕过 put（如第三方 mod 直接操作 activeEffects），
 *       其属性修改器也不会被应用到实体属性上</li>
 * </ul>
 *
 * <p>由于 1.21 中 addAttributeModifiers 不再传入 LivingEntity 参数，
 * 使用 {@link Global#effectBlockFlag} ThreadLocal 与 LivingEntityMixin 通信。
 */
@Mixin(MobEffect.class)
public abstract class MobEffectMixin {

    @Inject(
            method = "addAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void maidspell$blockAttributeModifiers(AttributeMap attributeMap, int amplifier, CallbackInfo ci) {
        if (Boolean.TRUE.equals(Global.effectBlockFlag.get())) {
            Global.effectBlockFlag.remove();
            ci.cancel();
        }
    }
}
