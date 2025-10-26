package com.github.yimeng261.maidspell.client.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.KeyBinds;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketMaidList;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端按键事件处理器
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT)
public class ClientKeyHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // 检查末影腰包GUI按键
        if (KeyBinds.OPEN_ENDER_POCKET_GUI.consumeClick()) {
            if (mc.player != null) {
                mc.player.connection.send(new C2SEnderPocketMaidList(false));
            }
        }
    }
}
