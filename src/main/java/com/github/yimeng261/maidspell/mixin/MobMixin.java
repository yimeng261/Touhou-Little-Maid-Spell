package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            // 检查女仆是否装备了锚定核心饰品
            if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                Global.LOGGER.debug("Maid {} does not have anchor_core, allowing conversion", maid.getUUID());
                return;
            }

            Global.LOGGER.debug("阻止女仆 {} 被转换成 {} (anchor_core protection)",
                maid.getUUID(), entityType.getDescriptionId());

            // 取消转换操作，返回null
            cir.setReturnValue(null);
        }
    }

    /**
     * 拦截dropCustomDeathLoot方法，阻止女仆在非正常死亡时掉落装备
     * 
     * @param damageSource 伤害源
     * @param looting 抢夺等级
     * @param recentlyHit 是否最近被击中
     * @param ci 回调信息
     */
    @Inject(method = "dropCustomDeathLoot", 
            at = @At("HEAD"), 
            cancellable = true)
    protected void preventMaidLootDrop(DamageSource damageSource, int looting, boolean recentlyHit, CallbackInfo ci) {
        if ((Object) this instanceof EntityMaid maid) {
            if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                Global.LOGGER.debug("Maid {} does not have anchor_core, allowing loot drop", maid.getUUID());
                return;
            }

            if (maid.getHealth() > 0.0f) {
                Global.LOGGER.debug("阻止血量大于0的女仆 {} 掉落战利品 (anchor_core protection)", maid.getUUID());
                ci.cancel();
            }
        }
    }
}
