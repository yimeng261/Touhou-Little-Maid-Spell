package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketPushUpdate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 末影腰包饰品实现
 * 不需要特殊的事件处理，主要功能通过按键和GUI实现
 */
public class EnderPocketBauble implements IMaidBauble {
    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        try {
            // 在客户端更新本地数据
            if (maid.level().isClientSide()) {
                ClientHandler.handleBaubleChange();
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
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        try {
            // 在客户端更新本地数据
            if (maid.level().isClientSide()) {
                ClientHandler.handleBaubleChange();
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
            player.connection.send(new S2CEnderPocketPushUpdate(maidInfos, true));
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
