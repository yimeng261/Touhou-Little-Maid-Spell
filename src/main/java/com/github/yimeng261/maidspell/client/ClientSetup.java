package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端设置类
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(MaidSpellBlocks.SCARLET_ZHUHUA.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(MaidSpellBlocks.POTTED_SCARLET_ZHUHUA.get(), RenderType.cutout());
        });
    }
}
