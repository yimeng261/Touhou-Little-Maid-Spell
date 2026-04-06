package com.github.yimeng261.maidspell.item.bauble.silverCercis;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import net.minecraft.world.entity.LivingEntity;
import oshi.util.tuples.Pair;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;



public class SilverCercisBauble implements IMaidBauble {
    private static final ConcurrentHashMap<UUID, Pair<Integer,Integer>> maidCercisMap = new ConcurrentHashMap<>();

    public static void handleSilverCercis(EntityMaid maid, LivingEntity target, DataItem dataItem) {
        UUID uuid = maid.getUUID();
        Pair<Integer,Integer> record = maidCercisMap.computeIfAbsent(uuid, k -> new Pair<>(0, 0));
        int tick = maid.tickCount;
        float multiplier = BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL) ? 2.0f : 1.0f;
        if(record.getA() >= Config.silverCercisTriggerCount || tick - record.getB() > Config.silverCercisCooldownTicks || BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)){
            if(TrueDamageUtil.dealTrueDamage(target, dataItem.getAmount()*(float)Config.silverCercisTrueDamageMultiplier*multiplier,maid)){
                maidCercisMap.put(uuid, new Pair<>(0,tick));
            }
        }else{
            maidCercisMap.put(uuid, new Pair<>(record.getA() + 1, tick));
        }

    }
} 