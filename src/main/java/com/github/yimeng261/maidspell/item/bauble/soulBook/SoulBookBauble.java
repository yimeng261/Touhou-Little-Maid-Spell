package com.github.yimeng261.maidspell.item.bauble.soulBook;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;

import net.minecraft.world.item.ItemStack;
import oshi.util.tuples.Pair;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 魂之书饰品实现
 * 提供伤害保护：当伤害超过女仆生命值20%时，将伤害降为20%
 * 同时实现伤害间隔检测：若两次伤害间隔不超过10tick则取消本次伤害
 */
public class SoulBookBauble implements IMaidBauble {
    private static final Map<UUID, Integer> lastHurtTimeMap = new ConcurrentHashMap<>();

    public static Pair<Boolean, Float> damageCalc(EntityMaid maid, float originalDamage) {
        UUID maidId = maid.getUUID();
        int currentTime = maid.tickCount;
        int lastHurtTime = lastHurtTimeMap.computeIfAbsent(maidId, (uuid) -> maid.tickCount);
        int timeDiff = currentTime - lastHurtTime;
        float damageThreshold = Math.min(originalDamage, maid.getMaxHealth() * (float)Config.soulBookDamageThresholdPercent);

        return new Pair<>(timeDiff > Config.soulBookDamageIntervalThreshold, damageThreshold);
    }

    public static void recordHurt(EntityMaid maid) {
        lastHurtTimeMap.put(maid.getUUID(), maid.tickCount);
    }

    public static boolean hasOwnerProtection(UUID ownerId) {
        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(ownerId);
        if (maids == null) {
            return false;
        }

        for (EntityMaid maid : maids.values()) {
            if (maid != null
                    && !maid.isRemoved()
                    && maid.isAddedToWorld()
                    && maid.isAlive()
                    && ownerId.equals(maid.getOwnerUUID())
                    && BaubleStateManager.hasBauble(maid, MaidSpellItems.SOUL_BOOK)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        if (!maid.level().isClientSide()) {
            cleanupMaid(maid.getUUID());
        }
    }

    public static void cleanupMaid(UUID maidId) {
        lastHurtTimeMap.remove(maidId);
    }

    public static void clearSession() {
        lastHurtTimeMap.clear();
    }
}

