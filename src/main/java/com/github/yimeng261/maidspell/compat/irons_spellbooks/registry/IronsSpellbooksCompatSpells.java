package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.MagicShotgunSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.ModifiedStarfallSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.ModifiedTeleportSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.StarShadowStrikeSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.SwordPrisonSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.VoidPhaseSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class IronsSpellbooksCompatSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, MaidSpellMod.MOD_ID);

    public static final RegistryObject<AbstractSpell> MODIFIED_STARFALL =
            SPELLS.register("starfall_modified", ModifiedStarfallSpell::new);
    public static final RegistryObject<AbstractSpell> MAGIC_SHOTGUN =
            SPELLS.register("magic_shotgun", MagicShotgunSpell::new);
    public static final RegistryObject<AbstractSpell> VOID_PHASE =
            SPELLS.register("void_phase", VoidPhaseSpell::new);
    public static final RegistryObject<AbstractSpell> SWORD_PRISON =
            SPELLS.register("sword_prison", SwordPrisonSpell::new);
    public static final RegistryObject<AbstractSpell> MODIFIED_TELEPORT =
            SPELLS.register("teleport_modified", ModifiedTeleportSpell::new);
    public static final RegistryObject<AbstractSpell> STAR_SHADOW_STRIKE =
            SPELLS.register("star_shadow_strike", StarShadowStrikeSpell::new);

    private IronsSpellbooksCompatSpells() {
    }

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
}
