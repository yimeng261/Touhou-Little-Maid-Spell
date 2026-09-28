package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SpellbreakingEchoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class SpellbreakingEchoRenderer extends LocalBlackHoleRenderer<SpellbreakingEchoEntity> {
    private static final ResourceLocation CENTER = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/spellbreaking_echo/black_hole_smooth.png");
    private static final ResourceLocation BEAM = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/spellbreaking_echo/beam.png");

    public SpellbreakingEchoRenderer(EntityRendererProvider.Context context) {
        super(context, CENTER, BEAM, 0xD2D2D2, 0xB9B9B9, 230, 200, 30,
                SpellbreakingEchoEntity.HOVER_ABOVE_EYES, 1.0F);
    }
}
