package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketMaidProxyCache;
import com.github.yimeng261.maidspell.stagewright.support.ProxyHealthProbe;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProxyHealthClient {
    private ProxyHealthClient() { }

    public static void handle(ProxyHealthProbe.Request request) {
        var maid = EnderPocketMaidProxyCache.find(Minecraft.getInstance().level, request.entityId());
        PacketDistributor.sendToServer(new ProxyHealthProbe.Reply(request.request(), maid == null ? Float.NaN : maid.getHealth()));
    }
}
