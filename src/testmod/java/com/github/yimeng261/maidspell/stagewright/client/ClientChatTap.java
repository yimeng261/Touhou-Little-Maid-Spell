package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.ChatTap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** 集成服拓扑：把客户端收到的系统消息记进 {@link ChatTap}，供场景核对玩家实际看到的提示。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT)
public final class ClientChatTap {
    private ClientChatTap() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        ChatTap.add(event.getMessage(), event.isOverlay());
    }
}
