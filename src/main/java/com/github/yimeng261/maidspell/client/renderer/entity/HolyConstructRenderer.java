package com.github.yimeng261.maidspell.client.renderer.entity;

import com.github.yimeng261.maidspell.client.model.HolyConstructModel;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class HolyConstructRenderer extends AbstractSpellCastingMobRenderer {
    public HolyConstructRenderer(EntityRendererProvider.Context context) {
        super(context, new HolyConstructModel());
    }
}
