package com.github.yimeng261.maidspell.item.bauble.soulBook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import oshi.util.tuples.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 魂之书饰品实现
 * 提供伤害保护：当伤害超过女仆生命值20%时，将伤害降为20%
 * 同时实现伤害间隔检测：若两次伤害间隔不超过10tick则取消本次伤害
 */
public class SoulBookBauble implements IExtendBauble {
    public static final Logger LOGGER = LogUtils.getLogger();

    // 存储每个女仆上次受伤的时间（tick）
    public static final Map<UUID, Integer> lastHurtTimeMap = new HashMap<>();
    public static final Map<UUID, Integer> maidSoulBookCount = new HashMap<>();

    public static Pair<Boolean, Float> damageCalc(EntityMaid maid, float originalDamage) {
        UUID maidId = maid.getUUID();
        int currentTime = maid.tickCount;
        int lastHurtTime = lastHurtTimeMap.computeIfAbsent(maidId, (uuid) -> maid.tickCount);
        int timeDiff = currentTime - lastHurtTime;
        float damageThreshold = Math.min(originalDamage, maid.getMaxHealth() * (float)Config.soulBookDamageThresholdPercent);

        return new Pair<>(timeDiff > Config.soulBookDamageIntervalThreshold, damageThreshold);
    }

    @Override
    public void onAdd(EntityMaid maid) {
        UUID id = maid.getOwnerUUID();
        int count = maidSoulBookCount.getOrDefault(id, 0);
        maidSoulBookCount.put(id, ++count);
    }

    @Override
    public void onRemove(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        lastHurtTimeMap.remove(maidId);
        UUID id = maid.getOwnerUUID();
        int count = maidSoulBookCount.getOrDefault(id, 0);
        maidSoulBookCount.put(id, Math.max(0, count - 1));
    }
}

