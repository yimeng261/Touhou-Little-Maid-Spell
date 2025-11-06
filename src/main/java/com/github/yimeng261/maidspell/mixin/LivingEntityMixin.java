package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.chaosBook.ChaosBookBauble;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.mojang.logging.LogUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import oshi.util.tuples.Pair;

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
            // 检查GameProfile是否为null，防止在玩家初始化过程中出现空指针异常
            if(player.getGameProfile()==null){
                return;
            }
            if(SoulBookBauble.maidSoulBookCount.getOrDefault(player.getGameProfile().getId(), 0) == 0) {
                return;
            }

            if(!maidspell$isCommonHurt(health, entity)){
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

        SliverCercisBauble_process(dataItem); //处理紫荆(计算减伤之前)

        if (!maidspell$isCommonHurt(health, entity)){
            ci.cancel();
            return;
        }

        SoulBookBauble_process(dataItem); //处理魂之书(最先计算是否取消)

        Global.bauble_hurtCalc_pre.forEach((item,func)->{
            if(BaubleStateManager.hasBauble(maid, item)){
                func.apply(dataItem);
            }
        });

        Global.bauble_hurtCalc_final.forEach((item,func)->{
            if(BaubleStateManager.hasBauble(maid, item)){
                func.apply(dataItem);
            }
        });

        if(dataItem.isCanceled()){
            dataItem.setAmount(0);
        }

        float finalHealth = Math.max(0.0f, currentHealth - dataItem.getAmount());
        maid.getEntityData().set(DATA_HEALTH_ID, finalHealth);

        ci.cancel();
    }

    @Unique
    private boolean maidspell$isCommonHurt(float health, LivingEntity entity) {
        float nowHealth = entity.getHealth();
        List<CombatEntry> entries = ((CombatTrackerAccessor)(entity.getCombatTracker())).getEntries();

        if(entries.isEmpty()){
            maidspell$LOGGER.debug("[MaidSpell] no combat entries found");
            return false;
        }
        maidspell$LOGGER.debug("[MaidSpell] nowHealth: {}, health: {}, last damage: {}", nowHealth, health, entries.get(entries.size()-1).damage());
        if(Math.abs(entries.get(entries.size()-1).damage()-(nowHealth-health))>0.1) {
            maidspell$LOGGER.debug("[MaidSpell] illegal damage!");
            return false;
        }
        return true;
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // 安全检查：确保伤害源不为空
        if (damageSource == null) {
            maidspell$LOGGER.warn("[MaidSpell] Received null damage source for entity {}", entity.getUUID());
            return;
        }

        if(damageSource instanceof InfoDamageSource){
            return;
        }

        if(entity instanceof Player || entity instanceof EntityMaid){
            return;
        }

        Entity sourceEntity = damageSource.getEntity();
        Entity directEntity = damageSource.getDirectEntity();

        if(sourceEntity instanceof EntityMaid maid){
            ChaosBookBauble.chaosBookProcess(maid, entity);
            WoundRimeBladeBauble.updateWoundRimeMap(maid,entity,amount);
        }else if(directEntity instanceof EntityMaid maid){
            ChaosBookBauble.chaosBookProcess(maid, entity);
            WoundRimeBladeBauble.updateWoundRimeMap(maid,entity,amount);
        }

    }

    @Unique
    private void SoulBookBauble_process(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if(!BaubleStateManager.hasBauble(maid, MaidSpellItems.SOUL_BOOK)){
            return;
        }
        float damage = dataItem.getAmount();
        Pair<Boolean, Float> result = SoulBookBauble.damageCalc(maid, damage);
        if (!result.getA()) {
            maidspell$LOGGER.debug("[SoulBookBauble] Damage cancelled for maid {} due to insufficient interval", maid.getUUID());
            dataItem.setCanceled(true);
            return;
        }
        dataItem.setAmount(result.getB());
        SoulBookBauble.lastHurtTimeMap.put(maid.getUUID(), maid.tickCount);
    }

    @Unique
    private void SliverCercisBauble_process(DataItem dataItem) {
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
        SilverCercisBauble.handleSilverCercis(maid,target,dataItem);
    }
}
