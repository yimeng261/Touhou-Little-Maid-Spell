package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.mixin.accessor.CombatTrackerMixin;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.LivingEntity;
import oshi.util.tuples.Pair;

public final class MaidDamageProcessor {

    private MaidDamageProcessor() {}

    public record MaidHealthChange(boolean writeHealth, float health) {
        public static MaidHealthChange block() {
            return new MaidHealthChange(false, 0.0f);
        }

        public static MaidHealthChange write(float health) {
            return new MaidHealthChange(true, health);
        }
    }

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
        SoulBookBauble.recordHurt(maid);
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

    public static MaidHealthChange processMaidHealthDecrease(EntityMaid maid, float requestedHealth) {
        float currentHealth = maid.getHealth();
        DataItem dataItem = new DataItem(maid, currentHealth - requestedHealth);

        processSilverCercis(dataItem);

        if (!isCommonHurt(requestedHealth, maid)) {
            return MaidHealthChange.block();
        }

        processSoulBook(dataItem);
        applyBaubleHandlers(dataItem, maid);

        if (dataItem.isCanceled()) {
            dataItem.setAmount(0.0f);
        }

        return MaidHealthChange.write(Math.max(0.0f, currentHealth - dataItem.getAmount()));
    }

    private static void processSilverCercis(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.SLIVER_CERCIS)) {
            return;
        }
        LivingEntity target = maid.getLastAttacker();
        if (target == null) {
            return;
        }
        if (!target.isAlive()) {
            target = maid.getTarget();
        }

        SilverCercisBauble.handleSilverCercis(maid, target, dataItem);
    }
}
