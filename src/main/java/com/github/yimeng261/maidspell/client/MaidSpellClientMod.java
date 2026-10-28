package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.gui.SpellWhiteListScreen;
import com.github.yimeng261.maidspell.client.gui.WinefoxChallengeConfigScreen;
import com.github.yimeng261.maidspell.client.model.AscensionHaloModel;
import com.github.yimeng261.maidspell.client.model.UnholyHaloModel;
import com.github.yimeng261.maidspell.client.overlay.EnderPocketHudOverlay;
import com.github.yimeng261.maidspell.client.particle.VoidSpellParticle;
import com.github.yimeng261.maidspell.client.renderer.entity.WindSeekingBellRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.compat.travelerstitles.client.TravelerTitlesStructureClient;
import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.MaidSpellContainers;
import com.github.yimeng261.maidspell.particle.MaidSpellParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.github.yimeng261.maidspell.client.resource.LegacyPackRepositorySource;

@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MaidSpellClientMod {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            IronsSpellbooksCompat.initClientSetup();
            registerCompassAngleProperty();
            registerTravelerTitlesCompat();
        });
    }

    private static void registerTravelerTitlesCompat() {
        if (ModList.get().isLoaded("travelerstitles")) {
            NeoForge.EVENT_BUS.register(TravelerTitlesStructureClient.class);
        }
    }

    /**
     * 观星罗盘的指针朝向。
     * <p>{@code angle} 不是通用谓词，原版只给 {@code Items.COMPASS} 和 {@code Items.RECOVERY_COMPASS} 各注册了一份，所以继承 {@code CompassItem} 并不会自动带上它，必须按物品再注册一次。
     * <p>目标取法比原版简单：观星罗盘只指结构，不存在"没绑定时指向出生点"这一档，没绑过就返回 null，
     * {@code CompassItemPropertyFunction} 会退化成随机转圈，正好表达"还没找到目标"。
     */
    private static void registerCompassAngleProperty() {
        ItemProperties.register(MaidSpellItems.STARWATCH_COMPASS.get(), ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker != null ? tracker.target().orElse(null) : null;
                }));
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MaidSpellContainers.SPELL_WHITE_LIST_CONTAINER.get(), SpellWhiteListScreen::new);
        event.register(MaidSpellContainers.WINEFOX_CHALLENGE_CONFIG.get(), WinefoxChallengeConfigScreen::new);
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
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        IronsSpellbooksCompat.initClientModels(event);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IronsSpellbooksCompat.initClientExtensions(event);
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
