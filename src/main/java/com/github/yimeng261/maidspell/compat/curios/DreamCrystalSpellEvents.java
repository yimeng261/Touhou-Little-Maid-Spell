package com.github.yimeng261.maidspell.compat.curios;

import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** 佩戴梦云水晶的玩家铁魔法法术无冷却；仅在 curios 与铁魔法都加载时注册。 */
public final class DreamCrystalSpellEvents {
    private DreamCrystalSpellEvents() {
    }

    @SubscribeEvent
    public static void cooldown(SpellCooldownAddedEvent.Pre event) {
        if (!DreamCrystalCurios.findCrystal(event.getEntity()).isEmpty()) {
            event.setEffectiveCooldown(0);
        }
    }
}
