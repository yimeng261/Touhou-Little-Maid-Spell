package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.CompanionBlackHoleSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.SpellbreakingEchoSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.MagicShotgunSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.ModifiedStarfallSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.ModifiedTeleportSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.StarShadowStrikeSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.SwordPrisonSpell;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.TripleStarArrowSpell;
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
    /** 三矢连星：万法酒狐专属，一次施法连发三支魔法箭，玩家无获取途径。 */
    public static final RegistryObject<AbstractSpell> TRIPLE_STAR_ARROW =
            SPELLS.register("triple_star_arrow", TripleStarArrowSpell::new);
    /**
     * 伴星黑洞：头顶 5 格的跟随型小黑洞。
     *
     * <p>注意它<b>不</b>实现 {@code BossExclusiveSpell}：作者明确要求战胜星之魔女后
     * 交易栏里卖「1 级伴星黑洞卷轴」，所以它是玩家可以正常拿到的法术。
     */
    public static final RegistryObject<AbstractSpell> COMPANION_BLACK_HOLE =
            SPELLS.register("companion_black_hole", CompanionBlackHoleSpell::new);
    public static final RegistryObject<AbstractSpell> SPELLBREAKING_ECHO =
            SPELLS.register("spellbreaking_echo", SpellbreakingEchoSpell::new);

    private IronsSpellbooksCompatSpells() {
    }

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
}
