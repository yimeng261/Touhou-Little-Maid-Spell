package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.coremod.HurtHeadCoremodHooks;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.github.yimeng261.maidspell.utils.FoxLeafOwnerEffectHelper;
import com.github.yimeng261.maidspell.utils.MaidDamageProcessor;
import com.github.yimeng261.maidspell.utils.MaidHealthWriteGuard;
import com.mojang.logging.LogUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;

import java.util.Map;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LivingEntity的Mixin，用于修改setHealth方法
 * 当EntityMaid装备SoulBookBauble时，对伤害进行额外处理
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private static final Logger maidspell$LOGGER = LogUtils.getLogger();
    
    @Final
    @Shadow
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;

    @Shadow
    public abstract MobEffectInstance removeEffectNoUpdate(MobEffect effect);

    @Shadow
    protected abstract void onEffectRemoved(MobEffectInstance effectInstance);

    @Shadow public abstract void remove(Entity.RemovalReason pReason);
    /**
     * 拦截setHealth方法调用
     */
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onSetHealth(float health, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        //处理heal
        float oldHealth = -114514;
        try{
            oldHealth = entity.getHealth();
        }catch(Exception ignored){
        }

        if(oldHealth == -114514){
            return;
        }

        if(oldHealth <= health){
            maidspell$handelHpIncrease(entity,health,ci);
        }else{
            maidspell$handelHpDecrease(entity,health,ci);
        }
        

    }

    @Unique
    private void maidspell$handelHpIncrease(LivingEntity entity, float health, CallbackInfo ci) {
        if(WoundRimeBladeBauble.handleWoundRimeMap(entity, health)){
            ci.cancel();
        }
    }

    @Unique
    private void maidspell$handelHpDecrease(LivingEntity entity, float health, CallbackInfo ci) {
        //处理玩家hurt
        if(entity instanceof ServerPlayer player) {
            if (HairpinBauble.captureRedirectedDamage(player, player.getHealth() - health)) {
                player.invulnerableTime = 0;
                ci.cancel();
                return;
            }

            if(SoulBookBauble.maidSoulBookCount.getOrDefault(player.getUUID(), 0) == 0) {
                return;
            }

            if(!MaidDamageProcessor.isCommonHurt(health, entity)){
                ci.cancel();
                return;
            }
        }

        //处理女仆setHealth
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }

        float currentHealth = maid.getHealth();
        DataItem dataItem = new DataItem(maid, currentHealth - health);

        maidspell$processSilverCercis(dataItem); //处理紫荆(计算减伤之前)

        if (!MaidDamageProcessor.isCommonHurt(health, entity)){
            ci.cancel();
            return;
        }

        MaidDamageProcessor.processSoulBook(dataItem); //处理魂之书(最先计算是否取消)

        MaidDamageProcessor.applyBaubleHandlers(dataItem, maid);

        if(dataItem.isCanceled()){
            dataItem.setAmount(0);
        }

        float finalHealth = Math.max(0.0f, currentHealth - dataItem.getAmount());
        MaidHealthWriteGuard.runBypassing(() -> maid.getEntityData().set(DATA_HEALTH_ID, finalHealth));

        ci.cancel();
    }

    /**
     * 方案二：@Redirect 重定向 addEffect 中的 activeEffects.put 调用。
     *
     * <p>拦截点在 Forge 事件 post 之后、效果真正写入 Map 之前。
     * 若 baubleEffectBlockFilter 中有任意过滤器返回 true，则不写入 Map，
     * 效果既不会 tick，也不会显示图标/粒子。
     *
     * <p>注意：put 被阻止后，原方法中紧随其后的 onEffectAdded 仍会被调用，
     * 因此还需要 MobEffectMixin 中的 addAttributeModifiers 注入作为第二重防护，
     * 确保属性修改器也不会被应用。
     */
    @Redirect(
        method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            ordinal = 0
        )
    )
    private Object maidspell$redirectEffectPut(Map<MobEffect, MobEffectInstance> map,
                                               Object key, Object value) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self instanceof EntityMaid maid
                && maidspell$shouldBlockMaidEffect(maid, (MobEffect) key)) {
            return null;
        }
        return map.put((MobEffect) key, (MobEffectInstance) value);
    }

    /**
     * Some mods bypass LivingEntity#addEffect and write activeEffects directly,
     * then invoke the vanilla update hook. Clean the already-written effect here.
     */
    @Inject(method = "onEffectAdded", at = @At("HEAD"), cancellable = true)
    private void maidspell$blockDirectEffectAdded(MobEffectInstance effectInstance,
                                                  Entity source,
                                                  CallbackInfo ci) {
        maidspell$cancelBlockedDirectEffect(effectInstance, ci);
    }

    @Inject(method = "onEffectUpdated", at = @At("HEAD"), cancellable = true)
    private void maidspell$blockDirectEffectUpdated(MobEffectInstance effectInstance,
                                                    boolean reapplyAttributeModifiers,
                                                    Entity source,
                                                    CallbackInfo ci) {
        maidspell$cancelBlockedDirectEffect(effectInstance, ci);
    }

    @Unique
    private void maidspell$cancelBlockedDirectEffect(MobEffectInstance effectInstance, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self instanceof EntityMaid maid
                && maidspell$shouldBlockMaidEffect(maid, effectInstance.getEffect())) {
            MobEffectInstance removed = this.removeEffectNoUpdate(effectInstance.getEffect());
            if (removed != null) {
                this.onEffectRemoved(removed);
            }
            ci.cancel();
        }
    }

    @Unique
    private boolean maidspell$shouldBlockMaidEffect(EntityMaid maid, MobEffect effect) {
        for (Map.Entry<Item, java.util.function.BiFunction<EntityMaid, MobEffect, Boolean>> entry
                : Global.baubleEffectBlockFilters.entrySet()) {
            if (BaubleStateManager.hasBauble(maid, entry.getKey())
                    && Boolean.TRUE.equals(entry.getValue().apply(maid, effect))) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (HurtHeadCoremodHooks.maidspell$isInsideInstrumentedHurt(entity)) {
            return;
        }

        // 安全检查：确保伤害源不为空
        if (damageSource == null) {
            maidspell$LOGGER.warn("[MaidSpell] Received null damage source for entity {}", entity.getUUID());
            return;
        }

        if(damageSource instanceof InfoDamageSource infoDamageSource){
            if((entity instanceof EntityMaid || entity instanceof Player)
                && !infoDamageSource.canHurtProtectedEntity()){
                cir.setReturnValue(false);
            }
            return;
        }
        
        if(entity instanceof Player || entity instanceof EntityMaid){
            return;
        }

        Global.HurtHeadContext hurtHeadContext = Global.dispatchHurtHeadHandlers(entity, damageSource, amount);
        if (hurtHeadContext.isHandled()) {
            cir.setReturnValue(hurtHeadContext.getReturnValue());
        }
    }

    @Inject(method = "canStandOnFluid(Lnet/minecraft/world/level/material/FluidState;)Z", at = @At("HEAD"), cancellable = true)
    private void maidspell$allowWaterWalk(FluidState fluidState, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof Player player) {
            if (FoxLeafOwnerEffectHelper.canOwnerStandOnFluid(player, fluidState)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (!(self instanceof EntityMaid maid) || maid.isUnderWater()) {
            return;
        }
        if (fluidState.is(FluidTags.WATER) && BaubleStateManager.hasBauble(maid, MaidSpellItems.FLOATING_FOX_LEAF)) {
            cir.setReturnValue(true);
            return;
        }
        if (fluidState.is(FluidTags.LAVA) && BaubleStateManager.hasBauble(maid, MaidSpellItems.MOLTEN_FOX_LEAF)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void maidspell$processSilverCercis(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if(!BaubleStateManager.hasBauble(maid, MaidSpellItems.SLIVER_CERCIS)){
            return;
        }
        LivingEntity target = maid.getLastAttacker();
        if(target == null){
            return;
        }
        if(!target.isAlive()){
            target = maid.getTarget();
        }

        SilverCercisBauble.handleSilverCercis(maid, target, dataItem);
    }
}
