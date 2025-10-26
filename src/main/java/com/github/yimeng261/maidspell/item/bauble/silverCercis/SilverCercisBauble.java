package com.github.yimeng261.maidspell.item.bauble.silverCercis;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import net.minecraft.world.entity.LivingEntity;
import oshi.util.tuples.Pair;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;



public class SilverCercisBauble implements IExtendBauble {
    private static final ConcurrentHashMap<UUID, Pair<Integer,Integer>> maidCercisMap = new ConcurrentHashMap<>();

    public static void handleSilverCercis(EntityMaid maid, LivingEntity target, DataItem dataItem) {
        UUID uuid = maid.getUUID();
        Pair<Integer,Integer> record = maidCercisMap.computeIfAbsent(uuid, k -> new Pair<>(0, 0));
        int tick = maid.tickCount;
        if(record.getA() >= 3 || tick - record.getB() > 5){
            if(TrueDamageUtil.dealTrueDamage(target, dataItem.getAmount()*0.8f,maid)){
                maidCercisMap.put(uuid, new Pair<>(0,tick));
            }
        }else{
            maidCercisMap.put(uuid, new Pair<>(record.getA() + 1, tick));
        }

    }
}