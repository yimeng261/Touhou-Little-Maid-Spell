package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * 方案一：注入 MobEffect.addAttributeModifiers，阻止有害效果的属性修改器被应用。
 *
 * <p>与 LivingEntityMixin 的 @Redirect Map.put 共同构成双重防护：
 * <ul>
 *   <li>@Redirect put → 效果不写入 activeEffects，不会 tick/显示</li>
 *   <li>本注入 → 即使效果绕过 put（如第三方 mod 直接操作 activeEffects），
 *       其属性修改器也不会被应用到实体属性上</li>
 * </ul>
 *
 * <p>过滤逻辑由 {@link Global#baubleEffectBlockFilters} 统一管理，
 * 各饰品在其 static 块中注册自身的过滤规则即可复用本 Mixin。
 */
@Mixin(MobEffect.class)
public abstract class MobEffectMixin {

    /**
     * 在属性修改器即将被应用时拦截。
     * <p>若目标实体是装备了某饰品的 EntityMaid，且该饰品的过滤器对当前效果返回 true，
     * 则取消本次 addAttributeModifiers 调用，属性不受影响。
     */
    @Inject(
        method = "addAttributeModifiers(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void maidspell$blockAttributeModifiers(LivingEntity livingEntity,
                                                    AttributeMap attributeMap,
                                                    int amplifier,
                                                    CallbackInfo ci) {
        if (!(livingEntity instanceof EntityMaid maid)) return;

        MobEffect self = (MobEffect)(Object)this;
        for (Map.Entry<Item, BiFunction<EntityMaid, MobEffect, Boolean>> entry : Global.baubleEffectBlockFilters.entrySet()) {
            if (BaubleStateManager.hasBauble(maid, entry.getKey())
                    && Boolean.TRUE.equals(entry.getValue().apply(maid, self))) {
                ci.cancel();
                return;
            }
        }
    }
}
