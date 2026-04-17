package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.IronsSpellbooksCompatClient;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class IronsSpellbooksCompat {
    public static final String MOD_ID = "irons_spellbooks";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private IronsSpellbooksCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void init(IEventBus eventBus) {
        if (!isLoaded()) {
            return;
        }
        IronsSpellbooksCompatItems.register(eventBus);
        IronsSpellbooksCompatEntities.register(eventBus);
    }

    public static void initClient(EntityRenderersEvent.RegisterRenderers event) {
        if (!isLoaded()) {
            return;
        }
        IronsSpellbooksCompatClient.onRegisterEntityRenderers(event);
    }
}
