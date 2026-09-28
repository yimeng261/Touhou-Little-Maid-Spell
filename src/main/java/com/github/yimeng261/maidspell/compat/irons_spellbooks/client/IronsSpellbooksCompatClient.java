package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model.GenericSpellHumanoidModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.CompanionBlackHoleRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.GenericSpellHumanoidRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.SpellbreakingEchoRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.StarShadowStrikeRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.WinefoxSwordProjectileRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import io.redspace.ironsspellbooks.entity.spells.comet.CometRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class IronsSpellbooksCompatClient {
    private IronsSpellbooksCompatClient() {
    }

    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.CORRUPTED_KNIGHT.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/corrupted_knight.geo.json"),
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/corrupted_knight.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.SHADOW_ASSASSIN.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/shadow_assassin.geo.json"),
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/shadow_assassin.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.ELF_TEMPLAR.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/elf_templar.geo.json"),
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/elf_templar.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.HOLY_CONSTRUCT.get(), context ->
                new GenericSpellHumanoidRenderer(context, new GenericSpellHumanoidModel(
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/holy_construct.geo.json"),
                        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/holy_construct.png"))));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.MODIFIED_STARFALL_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.MODIFIED_STARFALL_COMET.get(), context ->
                new CometRenderer(context, 0.75F));
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.WINEFOX_SWORD_PROJECTILE.get(),
                WinefoxSwordProjectileRenderer::new);
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.STAR_SHADOW_STRIKE.get(),
                StarShadowStrikeRenderer::new);
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.COMPANION_BLACK_HOLE.get(),
                CompanionBlackHoleRenderer::new);
        event.registerEntityRenderer(IronsSpellbooksCompatEntities.SPELLBREAKING_ECHO.get(),
                SpellbreakingEchoRenderer::new);
    }
}
