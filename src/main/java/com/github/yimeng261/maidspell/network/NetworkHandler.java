package com.github.yimeng261.maidspell.network;

import com.github.yimeng261.maidspell.network.message.C2SEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketOpenInventory;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketPushUpdate;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络消息处理器
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2";

    public static void registerMessages(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(C2SEnderPocketMaidList.TYPE, C2SEnderPocketMaidList.STREAM_CODEC, NetworkHandler::handleEnderPocketRequestMaidList);
        registrar.playToServer(C2SEnderPocketOpenInventory.TYPE, C2SEnderPocketOpenInventory.STREAM_CODEC, NetworkHandler::handleEnderPocketOpenInventory);
        registrar.playToClient(S2CEnderPocketMaidList.TYPE, S2CEnderPocketMaidList.STREAM_CODEC, NetworkHandler::handleEnderPocketResponseMaidList);
        registrar.playToClient(S2CEnderPocketPushUpdate.TYPE, S2CEnderPocketPushUpdate.STREAM_CODEC, NetworkHandler::handleEnderPocketPushUpdate);
    }

    public static void handleEnderPocketRequestMaidList(C2SEnderPocketMaidList packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        packet.handle(serverPlayer);
    }

    public static void handleEnderPocketOpenInventory(C2SEnderPocketOpenInventory packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        packet.handle(serverPlayer);
    }

    public static void handleEnderPocketResponseMaidList(S2CEnderPocketMaidList packet, IPayloadContext context) {
        packet.handle();
    }

    public static void handleEnderPocketPushUpdate(S2CEnderPocketPushUpdate packet, IPayloadContext context) {
        packet.handle();
    }
}
