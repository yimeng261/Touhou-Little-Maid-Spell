package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.effect.VoidPhaseEffect;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class IronsSpellbooksCompatEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MaidSpellMod.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> VOID_PHASE =
            EFFECTS.register("void_phase", VoidPhaseEffect::new);

    private IronsSpellbooksCompatEffects() {
    }

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
