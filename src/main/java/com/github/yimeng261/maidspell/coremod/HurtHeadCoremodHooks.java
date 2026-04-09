package com.github.yimeng261.maidspell.coremod;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.IdentityHashMap;

public final class HurtHeadCoremodHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<IdentityHashMap<LivingEntity, Integer>> ACTIVE_HURT_CALLS =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private HurtHeadCoremodHooks() {
    }

    public static void maidspell$coremodDebug(String message) {
        LOGGER.debug("[MaidSpell/Coremod] {}", message);
    }

    public static void maidspell$coremodInfo(String message) {
        LOGGER.info("[MaidSpell/Coremod] {}", message);
    }

    public static void maidspell$coremodWarn(String message) {
        LOGGER.warn("[MaidSpell/Coremod] {}", message);
    }

    public static void maidspell$coremodError(String message) {
        LOGGER.error("[MaidSpell/Coremod] {}", message);
    }

    public static Boolean maidspell$enterHurtHook(LivingEntity entity, DamageSource damageSource, float amount) {
        if (entity == null) {
            return null;
        }

        IdentityHashMap<LivingEntity, Integer> activeCalls = ACTIVE_HURT_CALLS.get();
        Integer depth = activeCalls.get(entity);
        if (depth != null) {
            activeCalls.put(entity, depth + 1);
            return null;
        }
        activeCalls.put(entity, 1);

        if (damageSource == null) {
            LOGGER.warn("[MaidSpell] Received null damage source for entity {}", entity.getUUID());
            return null;
        }

        if (damageSource instanceof InfoDamageSource) {
            if (entity instanceof EntityMaid || entity instanceof Player) {
                return Boolean.FALSE;
            }
            return null;
        }

        if (entity instanceof Player || entity instanceof EntityMaid) {
            return null;
        }

        try {
            Global.HurtHeadContext context = Global.dispatchHurtHeadHandlers(entity, damageSource, amount);
            return context.isHandled() ? context.getReturnValue() : null;
        } catch (Throwable throwable) {
            LOGGER.error("[MaidSpell] Coremod hurt hook failed for entity {}", entity.getUUID(), throwable);
            return null;
        }
    }

    public static void maidspell$exitHurtHook(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        IdentityHashMap<LivingEntity, Integer> activeCalls = ACTIVE_HURT_CALLS.get();
        Integer depth = activeCalls.get(entity);
        if (depth == null) {
            return;
        }

        if (depth <= 1) {
            activeCalls.remove(entity);
            if (activeCalls.isEmpty()) {
                ACTIVE_HURT_CALLS.remove();
            }
        } else {
            activeCalls.put(entity, depth - 1);
        }
    }

    public static boolean maidspell$isInsideInstrumentedHurt(LivingEntity entity) {
        return entity != null && ACTIVE_HURT_CALLS.get().containsKey(entity);
    }
}
