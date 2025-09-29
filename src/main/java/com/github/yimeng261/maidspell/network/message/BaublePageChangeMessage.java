package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.bauble.BaublePageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 饰品页面变化网络消息
 * 用于同步客户端和服务器之间的饰品页面状态
 */
public class BaublePageChangeMessage {
    
    private final int maidId;
    private final int newPage;
    
    public BaublePageChangeMessage(int maidId, int newPage) {
        this.maidId = maidId;
        this.newPage = newPage;
    }
    
    public BaublePageChangeMessage(FriendlyByteBuf buf) {
        this.maidId = buf.readInt();
        this.newPage = buf.readInt();
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(maidId);
        buf.writeInt(newPage);
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Entity maid = player.level().getEntity(maidId);
                if (maid != null) {
                    // 验证玩家是否有权限操作这个女仆
                    // 这里可以添加更多的权限检查
                    BaublePageManager.setCurrentPage(maid, newPage);
                }
            }
        });
        return true;
    }
}
