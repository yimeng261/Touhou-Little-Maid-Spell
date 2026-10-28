package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 星之魔女施放掌心黑洞期间不受法术反制影响。
 *
 * <p>铁魔法在执行 {@code cancelCast()} 和移除魔法效果之前发送这个可取消事件。
 * 在这里取消事件，可以只保护酒狐的掌心黑洞吟唱，而不会改变其他时间或其他
 * 生物的反制行为，也不会挡住星锚眩晕等由本模组主动触发的内部施法中断。</p>
 */
public final class WinefoxCounterspellGuard {
    private WinefoxCounterspellGuard() {
    }

    @SubscribeEvent
    public static void onCounterSpell(CounterSpellEvent event) {
        if (event.target instanceof MagicalWinefoxBossEntity boss
                && boss.isCastingPalmBlackHole()) {
            event.setCanceled(true);
        }
    }
}
