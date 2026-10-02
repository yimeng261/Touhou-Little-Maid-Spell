package com.github.yimeng261.maidspell.network;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.WinefoxSeatedAmbienceController;
import com.github.yimeng261.maidspell.compat.travelerstitles.client.TravelerTitlesStructureClient;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketHudRequest;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketMaidReady;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketOpenInventory;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketTeleport;
import com.github.yimeng261.maidspell.network.message.MaidClientRemovalGuardMessage;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketHudUpdate;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidData;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidSnapshot;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketPushUpdate;
import com.github.yimeng261.maidspell.network.message.TransmogNecklaceMessage;
import com.github.yimeng261.maidspell.network.message.TravelerTitlesStructureMessage;
import com.github.yimeng261.maidspell.network.message.WinefoxChallengeConfigMessage;
import com.github.yimeng261.maidspell.network.message.WinefoxStructureMusicMessage;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

/**
 * 网络消息处理器
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "7";

    public static void registerMessages(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(C2SEnderPocketMaidList.TYPE, C2SEnderPocketMaidList.STREAM_CODEC, toServer(C2SEnderPocketMaidList::handle));
        registrar.playToServer(C2SEnderPocketOpenInventory.TYPE, C2SEnderPocketOpenInventory.STREAM_CODEC, toServer(C2SEnderPocketOpenInventory::handle));
        registrar.playToServer(C2SEnderPocketHudRequest.TYPE, C2SEnderPocketHudRequest.STREAM_CODEC, toServer(C2SEnderPocketHudRequest::handle));
        registrar.playToServer(C2SEnderPocketMaidReady.TYPE, C2SEnderPocketMaidReady.STREAM_CODEC, toServer(C2SEnderPocketMaidReady::handle));
        registrar.playToServer(C2SEnderPocketTeleport.TYPE, C2SEnderPocketTeleport.STREAM_CODEC, toServer(C2SEnderPocketTeleport::handle));
        registrar.playToServer(TransmogNecklaceMessage.TYPE, TransmogNecklaceMessage.STREAM_CODEC, toServer(TransmogNecklaceMessage::handle));
        registrar.playToServer(WinefoxChallengeConfigMessage.TYPE, WinefoxChallengeConfigMessage.STREAM_CODEC, toServer(WinefoxChallengeConfigMessage::handle));
        registrar.playToClient(S2CEnderPocketMaidList.TYPE, S2CEnderPocketMaidList.STREAM_CODEC, NetworkHandler::handleEnderPocketResponseMaidList);
        registrar.playToClient(S2CEnderPocketPushUpdate.TYPE, S2CEnderPocketPushUpdate.STREAM_CODEC, NetworkHandler::handleEnderPocketPushUpdate);
        registrar.playToClient(S2CEnderPocketHudUpdate.TYPE, S2CEnderPocketHudUpdate.STREAM_CODEC, NetworkHandler::handleEnderPocketHudUpdate);
        registrar.playToClient(S2CEnderPocketMaidSnapshot.TYPE, S2CEnderPocketMaidSnapshot.STREAM_CODEC, NetworkHandler::handleEnderPocketMaidSnapshot);
        registrar.playToClient(S2CEnderPocketMaidData.TYPE, S2CEnderPocketMaidData.STREAM_CODEC, NetworkHandler::handleEnderPocketMaidData);
        registrar.playToClient(MaidClientRemovalGuardMessage.TYPE, MaidClientRemovalGuardMessage.STREAM_CODEC, NetworkHandler::handleMaidClientRemovalGuard);
        registrar.playToClient(MaidEntityRestoreMessage.TYPE, MaidEntityRestoreMessage.STREAM_CODEC, NetworkHandler::handleMaidEntityRestore);
        registrar.playToClient(WinefoxStructureMusicMessage.TYPE, WinefoxStructureMusicMessage.STREAM_CODEC, NetworkHandler::handleWinefoxStructureMusic);
        registrar.playToClient(TravelerTitlesStructureMessage.TYPE, TravelerTitlesStructureMessage.STREAM_CODEC, NetworkHandler::handleTravelerTitlesStructure);
    }

    /** 只处理服务端玩家发来的包。 */
    private static <T extends CustomPacketPayload> IPayloadHandler<T> toServer(BiConsumer<T, ServerPlayer> handler) {
        return (packet, context) -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                handler.accept(packet, serverPlayer);
            }
        };
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

    public static void handleEnderPocketMaidData(S2CEnderPocketMaidData packet, IPayloadContext context) {
        packet.handle();
    }

    public static void handleMaidClientRemovalGuard(MaidClientRemovalGuardMessage packet, IPayloadContext context) {
        packet.handle();
    }

    public static void handleMaidEntityRestore(MaidEntityRestoreMessage packet, IPayloadContext context) {
        com.github.yimeng261.maidspell.network.message.MaidEntityRestoreClientHandler.handlePayload(packet);
    }

    public static void handleWinefoxStructureMusic(WinefoxStructureMusicMessage packet, IPayloadContext context) {
        WinefoxSeatedAmbienceController.setInsideStellarEndshore(packet.insideStructure());
    }

    public static void handleTravelerTitlesStructure(TravelerTitlesStructureMessage packet, IPayloadContext context) {
        TravelerTitlesStructureClient.setStructure(packet.structureId());
    }
}
