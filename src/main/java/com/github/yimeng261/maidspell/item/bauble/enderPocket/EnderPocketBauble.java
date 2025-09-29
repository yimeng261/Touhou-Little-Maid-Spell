package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.EnderPocketMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

/**
 * 末影腰包饰品实现
 * 不需要特殊的事件处理，主要功能通过按键和GUI实现
 */
public class EnderPocketBauble implements IExtendBauble {
    @Override
    public void onAdd(EntityMaid maid) {
        try {
            // 在客户端更新本地数据
            if (maid.level().isClientSide()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHandler::handleBaubleChange);
            }
            
            // 在服务器端主动推送更新到所有相关客户端
            if (!maid.level().isClientSide() && maid.getOwner() instanceof ServerPlayer player) {
                pushEnderPocketDataToClient(player);
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to handle ender pocket bauble add for maid: {}", maid.getName().getString(), e);
        }
    }

    @Override
    public void onRemove(EntityMaid maid) {
        try {
            // 在客户端更新本地数据
            if (maid.level().isClientSide()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHandler::handleBaubleChange);
            }
            
            // 在服务器端主动推送更新到所有相关客户端
            if (!maid.level().isClientSide() && maid.getOwner() instanceof ServerPlayer player) {
                pushEnderPocketDataToClient(player);
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to handle ender pocket bauble remove for maid: {}", maid.getName().getString(), e);
        }
    }
    
    /**
     * 在服务器端主动推送末影腰包数据到客户端
     */
    private void pushEnderPocketDataToClient(ServerPlayer player) {
        try {
            List<EnderPocketService.EnderPocketMaidInfo> maidInfos = 
                    EnderPocketService.getPlayerEnderPocketMaids(player);
            
            // 使用便利方法创建服务器推送更新消息
            EnderPocketMessage message = EnderPocketMessage.serverPushUpdate(maidInfos);
            
            NetworkHandler.CHANNEL.sendTo(
                    message, 
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );
        } catch (Exception e) {
            Global.LOGGER.error("Failed to push ender pocket data to client for player: {}", player.getName().getString(), e);
        }
    }
    
    /**
     * 客户端处理类 - 只在客户端环境中存在
     */
    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        public static void handleBaubleChange() {
            try {
                // 确保Minecraft实例可用后再调用客户端方法
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    com.github.yimeng261.maidspell.client.event.MaidBackpackEnderPocketIntegration.forceRefreshData();
                }
            } catch (Exception e) {
                Global.LOGGER.error("Failed to handle bauble change on client side: {}", e.getMessage(), e);
            }
        }
    }
}
