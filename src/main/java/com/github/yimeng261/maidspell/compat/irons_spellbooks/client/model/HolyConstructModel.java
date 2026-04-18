package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model;

import com.github.yimeng261.maidspell.MaidSpellMod;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobModel;
import net.minecraft.resources.ResourceLocation;

public class HolyConstructModel extends AbstractSpellCastingMobModel {
    private static final ResourceLocation MODEL = new ResourceLocation(MaidSpellMod.MOD_ID, "geo/holy_construct.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/holy_construct.png");

    @Override
    public ResourceLocation getModelResource(AbstractSpellCastingMob object) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AbstractSpellCastingMob object) {
        return TEXTURE;
    }
}
