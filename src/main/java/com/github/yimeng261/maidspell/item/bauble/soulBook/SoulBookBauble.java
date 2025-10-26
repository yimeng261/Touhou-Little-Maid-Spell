package com.github.yimeng261.maidspell.item.bauble.soulBook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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
    // 伤害间隔阈值（10 tick）
    public static final int DAMAGE_INTERVAL_THRESHOLD = 10;

    public static Pair<Boolean, Float> damageCalc(EntityMaid maid, float originalDamage) {
        UUID maidId = maid.getUUID();
        int currentTime = maid.tickCount;
        int lastHurtTime = lastHurtTimeMap.computeIfAbsent(maidId, (uuid) -> maid.tickCount);
        int timeDiff = currentTime - lastHurtTime;
        float damageThreshold = Math.min(originalDamage, maid.getMaxHealth() * 0.2f);

        return new Pair<>(timeDiff > DAMAGE_INTERVAL_THRESHOLD, damageThreshold);
    }

    @Override
    public void onRemove(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        lastHurtTimeMap.remove(maidId);
    }
}

