package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.MaidBaubleSyncMessage;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * 女仆饰品服务端同步处理器
 * 在女仆进入世界时主动向客户端同步饰品数据
 * 解决魂符收放女仆后光环渲染不显示的问题
 */
@Mod.EventBusSubscriber
public class MaidBaubleSyncHandler {
    private static final Logger LOGGER = LogUtils.getLogger();



    /**
     * 监听玩家开始追踪实体事件（服务端）
     * 当玩家接近女仆或女仆传送到玩家附近时，立即同步饰品数据
     * 防止终末之环和晋升之环不显示
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        // 只处理女仆实体
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }

        // 只在服务端处理
        if (maid.level().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        LOGGER.debug("[饰品同步-服务端] 玩家 {} 开始追踪女仆 {}，发送饰品同步",
            player.getName().getString(), maid.getUUID());

        // 序列化饰品数据
        CompoundTag baubleData = maid.getMaidBauble().serializeNBT();

        // 向该玩家发送同步包
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new MaidBaubleSyncMessage(maid.getUUID(), baubleData)
        );
    }
}
