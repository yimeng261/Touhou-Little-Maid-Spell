package com.github.yimeng261.maidspell.compat.curios;

import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Registered only with both Curios and Iron's Spells installed. */
public final class DreamCrystalSpellEvents {
    private DreamCrystalSpellEvents() {}

    @SubscribeEvent
    public static void cooldown(SpellCooldownAddedEvent.Pre event) {
        if (!DreamCrystalCurios.findCrystal(event.getEntity()).isEmpty()) event.setEffectiveCooldown(0);
    }
}
