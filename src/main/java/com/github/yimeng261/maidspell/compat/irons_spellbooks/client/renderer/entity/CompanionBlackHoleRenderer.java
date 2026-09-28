package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.CompanionBlackHoleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class CompanionBlackHoleRenderer extends LocalBlackHoleRenderer<CompanionBlackHoleEntity> {
    private static final ResourceLocation CENTER = new ResourceLocation(MaidSpellMod.MOD_ID,
            "textures/entity/companion_black_hole/black_hole_smooth.png");
    private static final ResourceLocation BEAM = new ResourceLocation(MaidSpellMod.MOD_ID,
            "textures/entity/companion_black_hole/beam.png");

    public CompanionBlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context, CENTER, BEAM, 0xFFFFFF, 0xFF00FF, 255, 100, 20,
                CompanionBlackHoleEntity.HOVER_ABOVE_EYES, 1.0F);
    }
}
