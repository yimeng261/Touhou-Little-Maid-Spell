package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.client.gui.BlueNoteScreen;
import com.github.yimeng261.maidspell.client.model.SharedHaloModel;
import com.github.yimeng261.maidspell.client.model.UnholyHaloModel;
import com.github.yimeng261.maidspell.client.renderer.entity.WindSeekingBellRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.item.bauble.blueNote.contianer.MaidSpellContainers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.github.yimeng261.maidspell.client.resource.LegacyPackRepositorySource;

@Mod.EventBusSubscriber(modid = "touhou_little_maid_spell", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MaidSpellClientMod {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(MaidSpellContainers.BLUE_NOTE_CONTAINER.get(), BlueNoteScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBinds.OPEN_ENDER_POCKET_GUI);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MaidSpellEntities.WIND_SEEKING_BELL.get(), WindSeekingBellRenderer::new);
        IronsSpellbooksCompat.initClient(event);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SharedHaloModel.LAYER_LOCATION, SharedHaloModel::createBodyLayer);
        // 注册不洁光环模型
        event.registerLayerDefinition(UnholyHaloModel.LAYER_LOCATION, UnholyHaloModel::createBodyLayer);
    }
} 
