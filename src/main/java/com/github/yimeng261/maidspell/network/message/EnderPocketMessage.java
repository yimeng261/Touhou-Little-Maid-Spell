package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 统一的末影腰包网络消息类
 * 替代原有的多个消息类，简化网络通信逻辑
 */
public class EnderPocketMessage {
    
    public enum Type {
        REQUEST_MAID_LIST,      // 请求女仆列表
        RESPONSE_MAID_LIST,     // 响应女仆列表
        OPEN_MAID_INVENTORY,    // 打开女仆背包
        SERVER_PUSH_UPDATE      // 服务器主动推送数据更新
    }
    
    private final Type type;
    private final int maidEntityId;
    private final List<EnderPocketService.EnderPocketMaidInfo> maidInfos;
    private final boolean fromMaidBackpack; // 标记请求是否来自女仆背包界面
    
    // 请求女仆列表
    public EnderPocketMessage(Type type) {
        this.type = type;
        this.maidEntityId = -1;
        this.maidInfos = new ArrayList<>();
        this.fromMaidBackpack = false;
    }
    
    // 请求女仆列表（带来源标记）
    public EnderPocketMessage(Type type, boolean fromMaidBackpack) {
        this.type = type;
        this.maidEntityId = -1;
        this.maidInfos = new ArrayList<>();
        this.fromMaidBackpack = fromMaidBackpack;
    }
    
    // 打开女仆背包
    public EnderPocketMessage(Type type, int maidEntityId) {
        this.type = type;
        this.maidEntityId = maidEntityId;
        this.maidInfos = new ArrayList<>();
        this.fromMaidBackpack = false;
    }
    
    // 响应女仆列表
    public EnderPocketMessage(Type type, List<EnderPocketService.EnderPocketMaidInfo> maidInfos) {
        this.type = type;
        this.maidEntityId = -1;
        this.maidInfos = new ArrayList<>(maidInfos);
        this.fromMaidBackpack = false;
    }
    
    // 响应女仆列表（带来源标记）
    public EnderPocketMessage(Type type, List<EnderPocketService.EnderPocketMaidInfo> maidInfos, boolean fromMaidBackpack) {
        this.type = type;
        this.maidEntityId = -1;
        this.maidInfos = new ArrayList<>(maidInfos);
        this.fromMaidBackpack = fromMaidBackpack;
    }
    
    public static void encode(EnderPocketMessage message, FriendlyByteBuf buf) {
        buf.writeEnum(message.type);
        buf.writeInt(message.maidEntityId);
        buf.writeBoolean(message.fromMaidBackpack);
        
        buf.writeInt(message.maidInfos.size());
        for (EnderPocketService.EnderPocketMaidInfo info : message.maidInfos) {
            buf.writeUUID(info.maidUUID);
            buf.writeUtf(info.maidName);
            buf.writeInt(info.maidEntityId);
        }
    }
    
    public static EnderPocketMessage decode(FriendlyByteBuf buf) {
        Type type = buf.readEnum(Type.class);
        int maidEntityId = buf.readInt();
        boolean fromMaidBackpack = buf.readBoolean();
        
        int size = buf.readInt();
        List<EnderPocketService.EnderPocketMaidInfo> maidInfos = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            UUID maidUUID = buf.readUUID();
            String maidName = buf.readUtf();
            int entityId = buf.readInt();
            maidInfos.add(new EnderPocketService.EnderPocketMaidInfo(maidUUID, maidName, entityId));
        }
        
        switch (type) {
            case RESPONSE_MAID_LIST:
            case SERVER_PUSH_UPDATE:
                return new EnderPocketMessage(type, maidInfos, fromMaidBackpack);
            case OPEN_MAID_INVENTORY:
                return new EnderPocketMessage(type, maidEntityId);
            case REQUEST_MAID_LIST:
            default:
                return new EnderPocketMessage(type, fromMaidBackpack);
        }
    }
    
    public static void handle(EnderPocketMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isServer()) {
                handleServerSide(message, context.getSender());
            } else {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClientSide(message));
            }
        });
        context.setPacketHandled(true);
    }
    
    private static void handleServerSide(EnderPocketMessage message, ServerPlayer player) {
        if (player == null) return;
        
        switch (message.type) {
            case REQUEST_MAID_LIST:
                List<EnderPocketService.EnderPocketMaidInfo> maidInfos = 
                        EnderPocketService.getPlayerEnderPocketMaids(player);
                
                if (!maidInfos.isEmpty()) {
                    EnderPocketMessage response = new EnderPocketMessage(Type.RESPONSE_MAID_LIST, maidInfos, message.fromMaidBackpack);
                    NetworkHandler.CHANNEL.sendTo(
                            response, player.connection.connection,
                            net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
                    );
                }
                break;
                
            case OPEN_MAID_INVENTORY:
                EnderPocketService.openMaidInventory(player, message.maidEntityId);
                break;
                
            case RESPONSE_MAID_LIST:
            case SERVER_PUSH_UPDATE:
                // 这些情况不应该在服务器端处理
                break;
        }
    }
    
    private static void handleClientSide(EnderPocketMessage message) {
        // 使用 DistExecutor 确保客户端专用代码只在客户端执行
        EnderPocketClientHandler.handleClientMessage(message.type, message.maidInfos, message.fromMaidBackpack);
    }
    
    // 便利方法
    public static EnderPocketMessage requestMaidList() {
        return new EnderPocketMessage(Type.REQUEST_MAID_LIST);
    }
    
    public static EnderPocketMessage requestMaidListFromBackpack() {
        return new EnderPocketMessage(Type.REQUEST_MAID_LIST, true);
    }
    
    public static EnderPocketMessage openMaidInventory(int maidEntityId) {
        return new EnderPocketMessage(Type.OPEN_MAID_INVENTORY, maidEntityId);
    }
    
    public static EnderPocketMessage serverPushUpdate(List<EnderPocketService.EnderPocketMaidInfo> maidInfos) {
        return new EnderPocketMessage(Type.SERVER_PUSH_UPDATE, maidInfos, true);
    }
}
