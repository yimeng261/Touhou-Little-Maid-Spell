package com.github.yimeng261.maidspell.compat.curios;

import com.mna.api.events.SpellCooldownCalculatingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DreamCrystalManaArtificeEvents {
    private DreamCrystalManaArtificeEvents() {}

    @SubscribeEvent
    public static void cooldown(SpellCooldownCalculatingEvent event) {
        if (!DreamCrystalCurios.findCrystal(event.getCaster()).isEmpty()) event.setCooldown(0);
    }
}
