package com.github.yimeng261.maidspell.network;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.backpack.IBackpackContainerScreen;
import com.github.yimeng261.maidspell.client.event.MaidBackpackEnderPocketIntegration;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketOpenInventory;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidList;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketPushUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

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
        List<EnderPocketService.EnderPocketMaidInfo> maidInfos =
                EnderPocketService.getPlayerEnderPocketMaids(serverPlayer);

        if (!maidInfos.isEmpty()) {
            S2CEnderPocketMaidList response = new S2CEnderPocketMaidList(maidInfos, packet.fromMaidBackpack());
            serverPlayer.connection.send(response);
        }
    }

    public static void handleEnderPocketOpenInventory(C2SEnderPocketOpenInventory packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        EnderPocketService.openMaidInventory(serverPlayer, packet.maidEntityId());
    }

    public static void handleEnderPocketResponseMaidList(S2CEnderPocketMaidList packet, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        // 更新女仆背包集成的数据
        MaidBackpackEnderPocketIntegration.updateEnderPocketData(packet.maidInfos());

        // 根据请求来源和当前界面决定显示方式
        if (packet.fromMaidBackpack()) {
            // 来自女仆背包界面的请求
            if (mc.screen instanceof IBackpackContainerScreen) {
                // 重新初始化界面以更新按钮
                mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
        } else {
            mc.setScreen(new EnderPocketScreen(packet.maidInfos()));
        }
    }

    public static void handleEnderPocketPushUpdate(S2CEnderPocketPushUpdate packet, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        // 服务器主动推送的数据更新
        MaidBackpackEnderPocketIntegration.updateEnderPocketData(packet.maidInfos());

        // 如果当前在女仆背包界面，刷新界面
        if (mc.screen instanceof IBackpackContainerScreen) {
            mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }
}
