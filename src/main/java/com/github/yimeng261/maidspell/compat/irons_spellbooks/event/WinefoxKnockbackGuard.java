package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 将酒狐受到的击退压到 {@value #KNOCKBACK_SCALE} 倍。
 * ISS 的呼啸之风绕过 LivingEntity.knockback，但仍发送此事件。
 */
public final class WinefoxKnockbackGuard {

    /** 保留可见的受击反馈，避免被推离战斗位置。 */
    private static final float KNOCKBACK_SCALE = 0.2F;

    private WinefoxKnockbackGuard() {
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof MagicalWinefoxBossEntity)) {
            return;
        }
        event.setStrength(event.getStrength() * KNOCKBACK_SCALE);
    }
}
