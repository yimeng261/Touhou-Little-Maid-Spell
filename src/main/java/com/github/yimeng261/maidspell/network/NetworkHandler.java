package com.github.yimeng261.maidspell.network;

import com.github.yimeng261.maidspell.network.message.C2SEnderPocketHudRequest;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketMaidReady;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketOpenInventory;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketTeleport;
import com.github.yimeng261.maidspell.network.message.MaidClientRemovalGuardMessage;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketHudUpdate;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidSnapshot;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketPushUpdate;
import com.github.yimeng261.maidspell.network.message.TransmogNecklaceMessage;
import com.github.yimeng261.maidspell.network.message.WinefoxChallengeConfigMessage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络消息处理器
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "5";

    public static void registerMessages(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(C2SEnderPocketMaidList.TYPE, C2SEnderPocketMaidList.STREAM_CODEC, NetworkHandler::handleEnderPocketRequestMaidList);
        registrar.playToServer(C2SEnderPocketOpenInventory.TYPE, C2SEnderPocketOpenInventory.STREAM_CODEC, NetworkHandler::handleEnderPocketOpenInventory);
        registrar.playToServer(C2SEnderPocketHudRequest.TYPE, C2SEnderPocketHudRequest.STREAM_CODEC, NetworkHandler::handleEnderPocketHudRequest);
        registrar.playToServer(C2SEnderPocketMaidReady.TYPE, C2SEnderPocketMaidReady.STREAM_CODEC, NetworkHandler::handleEnderPocketMaidReady);
        registrar.playToServer(C2SEnderPocketTeleport.TYPE, C2SEnderPocketTeleport.STREAM_CODEC, NetworkHandler::handleEnderPocketTeleport);
        registrar.playToServer(TransmogNecklaceMessage.TYPE, TransmogNecklaceMessage.STREAM_CODEC, NetworkHandler::handleTransmogNecklaceMessage);
        registrar.playToServer(WinefoxChallengeConfigMessage.TYPE, WinefoxChallengeConfigMessage.STREAM_CODEC, NetworkHandler::handleWinefoxChallengeConfig);
        registrar.playToClient(S2CEnderPocketMaidList.TYPE, S2CEnderPocketMaidList.STREAM_CODEC, NetworkHandler::handleEnderPocketResponseMaidList);
        registrar.playToClient(S2CEnderPocketPushUpdate.TYPE, S2CEnderPocketPushUpdate.STREAM_CODEC, NetworkHandler::handleEnderPocketPushUpdate);
        registrar.playToClient(S2CEnderPocketHudUpdate.TYPE, S2CEnderPocketHudUpdate.STREAM_CODEC, NetworkHandler::handleEnderPocketHudUpdate);
        registrar.playToClient(S2CEnderPocketMaidSnapshot.TYPE, S2CEnderPocketMaidSnapshot.STREAM_CODEC, NetworkHandler::handleEnderPocketMaidSnapshot);
        registrar.playToClient(MaidClientRemovalGuardMessage.TYPE, MaidClientRemovalGuardMessage.STREAM_CODEC, NetworkHandler::handleMaidClientRemovalGuard);
        registrar.playToClient(MaidEntityRestoreMessage.TYPE, MaidEntityRestoreMessage.STREAM_CODEC, NetworkHandler::handleMaidEntityRestore);
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

    public static void handleEnderPocketHudRequest(C2SEnderPocketHudRequest packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        packet.handle(serverPlayer);
    }

    public static void handleEnderPocketMaidReady(C2SEnderPocketMaidReady packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        packet.handle(serverPlayer);
    }

    public static void handleEnderPocketTeleport(C2SEnderPocketTeleport packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        packet.handle(serverPlayer);
    }

    public static void handleWinefoxChallengeConfig(WinefoxChallengeConfigMessage packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        packet.handle(serverPlayer);
    }

    public static void handleTransmogNecklaceMessage(TransmogNecklaceMessage packet, IPayloadContext context) {
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

    public static void handleEnderPocketHudUpdate(S2CEnderPocketHudUpdate packet, IPayloadContext context) {
        packet.handle();
    }

    public static void handleEnderPocketMaidSnapshot(S2CEnderPocketMaidSnapshot packet, IPayloadContext context) {
        packet.handle();
    }

    public static void handleMaidClientRemovalGuard(MaidClientRemovalGuardMessage packet, IPayloadContext context) {
        packet.handle();
    }

    public static void handleMaidEntityRestore(MaidEntityRestoreMessage packet, IPayloadContext context) {
        com.github.yimeng261.maidspell.network.message.MaidEntityRestoreClientHandler.handlePayload(packet);
    }
}
