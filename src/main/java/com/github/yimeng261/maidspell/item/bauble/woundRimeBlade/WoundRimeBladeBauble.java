package com.github.yimeng261.maidspell.item.bauble.woundRimeBlade;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 破愈咒锋饰品实现
 * 监听周围敌对实体的治疗并阻止
 */
public class WoundRimeBladeBauble implements IExtendBauble {

    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<LivingEntity,Float>> maidWoundRimeBladeMap = new ConcurrentHashMap<>();

    @Override
    public void onRemove(EntityMaid maid) {
        maidWoundRimeBladeMap.remove(maid.getUUID());
    }

    public static void updateWoundRimeMap(EntityMaid maid, LivingEntity entity,float damage) {
        if(maidWoundRimeBladeMap.containsKey(maid.getUUID())) {
            ConcurrentHashMap<LivingEntity,Float> map = maidWoundRimeBladeMap.get(maid.getUUID());
            float nowHealth = map.computeIfAbsent(entity, k -> entity.getHealth());
            map.put(entity, nowHealth - damage);
        }
    }

    public void onTick(EntityMaid maid, ItemStack baubleItem){
        if(maidWoundRimeBladeMap.containsKey(maid.getUUID())){
            ConcurrentHashMap<LivingEntity,Float> map = maidWoundRimeBladeMap.get(maid.getUUID());
            map.forEach((entity, health) -> {
                if(!entity.isAlive()){
                    map.remove(entity);
                    return;
                }
                float nowHealth = entity.getHealth();
                if(nowHealth > health){
                    TrueDamageUtil.dealTrueDamage(entity, nowHealth - health);
                }
            });
        }
    }

    static {
        Global.bauble_damageProcessors_pre.put(MaidSpellItems.itemDesc(MaidSpellItems.WOUND_RIME_BLADE),(event, maid) -> {
            LivingEntity entity = event.getEntity();
            ConcurrentHashMap<LivingEntity,Float> map = maidWoundRimeBladeMap.computeIfAbsent(maid.getUUID(), (uuid) -> new ConcurrentHashMap<>());
            if(!map.containsKey(entity)){
                map.put(entity, entity.getHealth());
            }else{
                if(map.get(entity) > entity.getHealth()){
                    map.put(entity, entity.getHealth());
                }
            }
            return null;
        });
    }
}
