package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import oshi.util.tuples.Pair;

/**
 * LivingEntity的Mixin，用于修改setHealth方法
 * 当EntityMaid装备SoulBookBauble时，对伤害进行额外处理
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Shadow
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;

    /**
     * 拦截setHealth方法调用
     */
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onSetHealth(float health, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        // 只处理EntityMaid
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }

        try {
            float currentHealth = maid.getHealth();
            //治疗则不处理
            if (health >= currentHealth) {
                return;
            }

            DataItem dataItem = new DataItem(maid, currentHealth - health);

            SliverCercisBauble_process(dataItem); //优先处理银链

            SoulBookBauble_process(dataItem); //优先处理魂之书

            Global.bauble_hurtProcessors_final.forEach((key, func) -> func.apply(dataItem));

            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                Function<DataItem, Void> func = Global.bauble_hurtProcessors_pre.getOrDefault(bauble.getDescriptionId(), (d) -> null);
                func.apply(dataItem);
            });

            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                Function<DataItem, Void> func = Global.bauble_hurtProcessors_final.getOrDefault(bauble.getDescriptionId(), (d) -> null);
                func.apply(dataItem);
            });

            if(dataItem.isCanceled()){
                dataItem.setAmount(0);
            }

            float finalHealth = Math.max(0.0f, currentHealth - dataItem.getAmount());
            maid.getEntityData().set(DATA_HEALTH_ID, finalHealth);
            
            ci.cancel();
            
        } catch (Exception e) {
            LOGGER.error("[SoulBookBauble] Error processing setHealth modification for maid {}", 
                    entity.getUUID(), e);
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
            LOGGER.debug("[SoulBookBauble] Damage cancelled for maid {} due to insufficient interval", maid.getUUID());
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
        if(!target.isAlive()){
            target = maid.getTarget();
        }
        TrueDamageUtil.dealTrueDamage(target, dataItem.getAmount()*0.8f);
    }
}
