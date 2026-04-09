package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model;

import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobModel;
import net.minecraft.resources.ResourceLocation;

public class GenericSpellHumanoidModel extends AbstractSpellCastingMobModel {
    private final ResourceLocation model;
    private final ResourceLocation texture;

    public GenericSpellHumanoidModel(ResourceLocation model, ResourceLocation texture) {
        this.model = model;
        this.texture = texture;
    }

    @Override
    public ResourceLocation getModelResource(AbstractSpellCastingMob object) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(AbstractSpellCastingMob object) {
        return texture;
    }
}
