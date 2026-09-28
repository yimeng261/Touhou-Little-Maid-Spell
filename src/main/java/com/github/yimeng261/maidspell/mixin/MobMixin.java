package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import com.github.yimeng261.maidspell.utils.PersistentEntityLifecycleGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob 层的生命周期保护与交互入口。
 */
@Mixin(Mob.class)
public class MobMixin {

    @WrapOperation(
        method = "interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;isAlive()Z")
    )
    private boolean maidspell$allowCustomLifecycleInteraction(Mob mob, Operation<Boolean> original) {
        return mob instanceof IAuthoritativeHealth ? !mob.isRemoved() : original.call(mob);
    }
    
    /**
     * 拦截 convertTo 方法，阻止受保护实体被转换成其他实体。判据只有一条，由 {@link PersistentEntityLifecycleGuard#shouldBlockConversion} 统一给出：
     * 内部是"星之魔女等受保护遭遇实体，**或**装备了锚定核心的女仆"。两种策略的判定顺序与合并方式都在那一处维护，
     * 这里不再重复判一次锚定核心——重复判会写出一段永远走不到的分支，让人误以为这里还有第二条独立判据。
     *
     * @param entityType 目标实体类型
     * @param bl 是否保留装备
     * @param cir 回调信息返回值
     */
    @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;",
            at = @At("HEAD"),
            cancellable = true)
    public <T extends Mob> void preventMaidConversion(EntityType<T> entityType, boolean bl, CallbackInfoReturnable<T> cir) {
        if (!PersistentEntityLifecycleGuard.shouldBlockConversion((Mob) (Object) this)) {
            return;
        }
        Global.LOGGER.debug("Prevented {} from converting to {} (lifecycle protection)",
            this, entityType.getDescriptionId());
        // 取消转换操作，返回 null
        cir.setReturnValue(null);
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
            if (!AnchorCoreProtection.hasAnchorCore(maid)) {
                Global.LOGGER.debug("Maid {} does not have anchor_core, allowing loot drop", maid.getUUID());
                return;
            }

            if (AnchorCoreProtection.shouldBlockAliveAnchoredDrop(maid)) {
                Global.LOGGER.debug("阻止血量大于0的女仆 {} 掉落战利品 (anchor_core protection)", maid.getUUID());
                ci.cancel();
            }
        }
    }
}
