package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.client.gui.BlueNoteScreen;
import com.github.yimeng261.maidspell.client.renderer.entity.WindSeekingBellRenderer;
import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.item.bauble.blueNote.contianer.MaidSpellContainers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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
    }
} 