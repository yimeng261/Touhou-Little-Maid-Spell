package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.mixin.CombatTrackerMixin;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.LivingEntity;
import oshi.util.tuples.Pair;

public final class MaidDamageProcessor {

    private MaidDamageProcessor() {}

    public static boolean isCommonHurt(float requestedHealth, LivingEntity entity) {
        float currentHealth = entity.getHealth();
        var entries = ((CombatTrackerMixin) entity.getCombatTracker()).getEntries();
        if (entries.isEmpty()) {
            return false;
        }
        return Math.abs(entries.get(entries.size() - 1).damage() - (currentHealth - requestedHealth)) <= 0.1f;
    }

    public static void processSoulBook(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.SOUL_BOOK)) {
            return;
        }
        Pair<Boolean, Float> result = SoulBookBauble.damageCalc(maid, dataItem.getAmount());
        if (!result.getA()) {
            dataItem.setCanceled(true);
            return;
        }
        dataItem.setAmount(result.getB());
        SoulBookBauble.lastHurtTimeMap.put(maid.getUUID(), maid.tickCount);
    }

    public static void applyBaubleHandlers(DataItem dataItem, EntityMaid maid) {
        Global.baubleSetHealthHandlers.forEach((item, func) -> {
            if (BaubleStateManager.hasBauble(maid, item)) {
                func.apply(dataItem);
            }
        });
        Global.baubleSetHealthFinalHandlers.forEach((item, func) -> {
            if (BaubleStateManager.hasBauble(maid, item)) {
                func.apply(dataItem);
            }
        });
    }
}
