package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.FoxLeafOwnerEffectHelper;
import com.github.yimeng261.maidspell.utils.MaidDamageProcessor;
import com.github.yimeng261.maidspell.utils.MaidHealthWriteGuard;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * LivingEntity的Mixin，用于修改setHealth方法
 * 当EntityMaid装备SoulBookBauble时，对伤害进行额外处理
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Final
    @Shadow
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;

    @Shadow
    public abstract MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect);

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

        MaidDamageProcessor.MaidHealthChange healthChange = MaidDamageProcessor.processMaidHealthDecrease(maid, health);
        if (healthChange.writeHealth()) {
            MaidHealthWriteGuard.runBypassing(() -> maid.getEntityData().set(DATA_HEALTH_ID, healthChange.health()));
        }

        ci.cancel();
    }

    /**
     * @Redirect 重定向 addEffect 中的 activeEffects.put 调用。
     *
     * <p>拦截点在 NeoForge 事件 post 之后、效果真正写入 Map 之前。
     * 若 baubleEffectBlockFilters 中有任意过滤器返回 true，则不写入 Map，
     * 效果既不会 tick，也不会显示图标/粒子。
     *
     * <p>同时设置 {@link Global#effectBlockFlag} 通知 MobEffectMixin 阻止属性修改器应用。
     */
    @SuppressWarnings("unchecked")
    @Redirect(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object maidspell$redirectEffectPut(Map<Holder<MobEffect>, MobEffectInstance> map,
                                               Object key, Object value) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof EntityMaid maid
                && maidspell$shouldBlockMaidEffect(maid, (Holder<MobEffect>) key)) {
            Global.effectBlockFlag.set(Boolean.TRUE);
            return null;
        }
        return map.put((Holder<MobEffect>) key, (MobEffectInstance) value);
    }

    /**
     * 部分模组绕过 LivingEntity#addEffect，直接写入 activeEffects，
     * 然后调用 vanilla 的更新钩子。在此清理已经写入的效果。
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
        LivingEntity self = (LivingEntity) (Object) this;
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
    private boolean maidspell$shouldBlockMaidEffect(EntityMaid maid, Holder<MobEffect> effect) {
        for (Map.Entry<Item, BiFunction<EntityMaid, Holder<MobEffect>, Boolean>> entry
                : Global.baubleEffectBlockFilters.entrySet()) {
            if (BaubleStateManager.hasBauble(maid, entry.getKey())
                    && Boolean.TRUE.equals(entry.getValue().apply(maid, effect))) {
                return true;
            }
        }
        return false;
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

}
