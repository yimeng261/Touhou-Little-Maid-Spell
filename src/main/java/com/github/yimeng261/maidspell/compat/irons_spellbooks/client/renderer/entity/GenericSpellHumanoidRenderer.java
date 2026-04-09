package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobRenderer;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class GenericSpellHumanoidRenderer extends AbstractSpellCastingMobRenderer {
    public GenericSpellHumanoidRenderer(EntityRendererProvider.Context context, AbstractSpellCastingMobModel model) {
        super(context, model);
    }
}
