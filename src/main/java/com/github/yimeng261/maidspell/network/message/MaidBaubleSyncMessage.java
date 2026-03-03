package com.github.yimeng261.maidspell.network.message;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 女仆饰品同步消息
 * 用于在女仆进入世界时从服务端向客户端同步饰品数据
 * 解决魂符收放女仆后光环渲染不显示的问题
 */
public class MaidBaubleSyncMessage {

    private final UUID maidUUID;
    private final CompoundTag baubleData;

    /**
     * 构造函数
     * @param maidUUID 女仆UUID
     * @param baubleData 饰品数据（ItemStackHandler序列化后的NBT）
     */
    public MaidBaubleSyncMessage(UUID maidUUID, CompoundTag baubleData) {
        this.maidUUID = maidUUID;
        this.baubleData = baubleData;
    }

    /**
     * 编码消息到网络缓冲区
     */
    public static void encode(MaidBaubleSyncMessage message, FriendlyByteBuf buf) {
        buf.writeUUID(message.maidUUID);
        buf.writeNbt(message.baubleData);
    }

    /**
     * 从网络缓冲区解码消息
     */
    public static MaidBaubleSyncMessage decode(FriendlyByteBuf buf) {
        UUID maidUUID = buf.readUUID();
        CompoundTag baubleData = buf.readNbt();
        return new MaidBaubleSyncMessage(maidUUID, baubleData);
    }

    /**
     * 处理消息
     */
    public static void handle(MaidBaubleSyncMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isServer()) {
                // 服务器端不处理此消息
                return;
            } else {
                // 客户端处理
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClientSide(message));
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 客户端处理逻辑
     */
    private static void handleClientSide(MaidBaubleSyncMessage message) {
        MaidBaubleSyncClientHandler.handleBaubleSync(message.maidUUID, message.baubleData);
    }

    public UUID getMaidUUID() {
        return maidUUID;
    }

    public CompoundTag getBaubleData() {
        return baubleData;
    }
}
