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

/**
 * Coremod 注入到 hurt 方法头部后统一调用的桥接类。
 *
 * <p>职责：
 * 1. 将现有 HurtHead 逻辑前移到子类 hurt 开头；
 * 2. 用线程内重入计数避免 child.hurt -> super.hurt 重复触发；
 * 3. 保持与 LivingEntityMixin.onHurt 现有行为一致。
 */
public final class HurtHeadCoremodHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<IdentityHashMap<LivingEntity, Integer>> ACTIVE_HURT_CALLS =
        ThreadLocal.withInitial(IdentityHashMap::new);

    private HurtHeadCoremodHooks() {
    }

    /**
     * 进入 hurt 头部。
     *
     * @return `null` 表示继续原方法；非空表示已决定返回值
     */
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

        if (damageSource instanceof InfoDamageSource infoDamageSource) {
            if ((entity instanceof EntityMaid || entity instanceof Player)
                && !infoDamageSource.canHurtProtectedEntity()) {
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

    /**
     * 退出 hurt 调用链。
     */
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

    /**
     * 供 LivingEntityMixin 判断本次 hurt 是否已经由 coremod 处理过。
     */
    public static boolean maidspell$isInsideInstrumentedHurt(LivingEntity entity) {
        return entity != null && ACTIVE_HURT_CALLS.get().containsKey(entity);
    }
}
