package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.gui.SpellWhiteListScreen;
import com.github.yimeng261.maidspell.client.model.AscensionHaloModel;
import com.github.yimeng261.maidspell.client.model.UnholyHaloModel;
import com.github.yimeng261.maidspell.client.overlay.EnderPocketHudOverlay;
import com.github.yimeng261.maidspell.client.particle.VoidSpellParticle;
import com.github.yimeng261.maidspell.client.renderer.entity.WindSeekingBellRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.MaidSpellContainers;
import com.github.yimeng261.maidspell.particle.MaidSpellParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.github.yimeng261.maidspell.client.resource.LegacyPackRepositorySource;

@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MaidSpellClientMod {

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MaidSpellContainers.SPELL_WHITE_LIST_CONTAINER.get(), SpellWhiteListScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBinds.OPEN_ENDER_POCKET_GUI);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_status"),
                new EnderPocketHudOverlay());
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MaidSpellEntities.WIND_SEEKING_BELL.get(), WindSeekingBellRenderer::new);
        IronsSpellbooksCompat.initClient(event);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MaidSpellParticles.VOID_SPELL.get(), VoidSpellParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AscensionHaloModel.LAYER_LOCATION, AscensionHaloModel::createBodyLayer);
        event.registerLayerDefinition(UnholyHaloModel.LAYER_LOCATION, UnholyHaloModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(new LegacyPackRepositorySource());
        }
    }
}
