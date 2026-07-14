package com.github.yimeng261.maidspell.client.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketMaidProxyCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT)
public final class EnderPocketMaidProxyEvents {
    private EnderPocketMaidProxyEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EnderPocketMaidProxyCache.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            EnderPocketMaidProxyCache.clear();
        }
    }
}
