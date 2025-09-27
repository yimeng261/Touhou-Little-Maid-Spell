package com.github.yimeng261.maidspell.client.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.KeyBinds;
import com.github.yimeng261.maidspell.network.message.EnderPocketMessage;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端按键事件处理器
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientKeyHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            
            // 检查末影腰包GUI按键
            if (KeyBinds.OPEN_ENDER_POCKET_GUI.consumeClick()) {
                if (mc.player != null) {
                    NetworkHandler.CHANNEL.sendToServer(EnderPocketMessage.requestMaidList());
                }
            }
        }
    }
}
