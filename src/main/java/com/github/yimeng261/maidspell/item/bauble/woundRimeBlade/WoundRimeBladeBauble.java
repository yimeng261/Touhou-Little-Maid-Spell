package com.github.yimeng261.maidspell.item.bauble.woundRimeBlade;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import oshi.util.tuples.Pair;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 破愈咒锋饰品实现
 * 监听周围敌对实体的治疗并阻止
 */
public class WoundRimeBladeBauble implements IExtendBauble {

    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<LivingEntity, Pair<Float,Integer>>> maidWoundRimeBladeMap = new ConcurrentHashMap<>();

    @Override
    public void onRemove(EntityMaid maid) {
        maidWoundRimeBladeMap.remove(maid.getUUID());
    }

    public static void updateWoundRimeMap(EntityMaid maid, LivingEntity entity,float damage) {
        if(!BaubleStateManager.hasBauble(maid,MaidSpellItems.WOUND_RIME_BLADE)) {
            return;
        }
        if(maidWoundRimeBladeMap.containsKey(maid.getUUID())) {
            if(entity instanceof Player || entity instanceof EntityMaid) {
                return;
            }
            ConcurrentHashMap<LivingEntity,Pair<Float,Integer>> map = maidWoundRimeBladeMap.get(maid.getUUID());
            float nowHealth = entity.getHealth();
            Pair<Float,Integer> record = map.computeIfAbsent(entity, k -> new Pair<>(nowHealth, Config.woundRimeBladeRecordDuration));
            float recordHealth = record.getA();
            float newHealth = Math.min(recordHealth,nowHealth) - damage;
            map.put(entity, new Pair<>(newHealth, record.getB() + Config.woundRimeBladeRecordDuration));
        }
    }

    public static boolean handleWoundRimeMap(LivingEntity entity,float health) {
        AtomicBoolean shouldCancel = new AtomicBoolean(false);
        maidWoundRimeBladeMap.forEach( (uuid, map) -> {
            if(!map.containsKey(entity)) {
                return;
            }
            Pair<Float,Integer> record = map.get(entity);
            map.put(entity, new Pair<>(health, record.getB() - 1));
            shouldCancel.set(true);
        });
        return shouldCancel.get();
    }


    public void onTick(EntityMaid maid, ItemStack baubleItem){
        ConcurrentHashMap<LivingEntity,Pair<Float,Integer>> map = maidWoundRimeBladeMap.computeIfAbsent(maid.getUUID(), k -> new ConcurrentHashMap<>());
        map.forEach((entity, record) -> {
            if(!entity.isAlive() || record.getB() == 0){
                map.remove(entity);
            }
        });
    }

}
