package com.github.yimeng261.maidspell.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 法术信息同步消息
 * 用于将单个女仆的法术信息同步到客户端
 * 数据格式：UUID + Map<模组ID, 法术名称>
 */
public class SpellSyncMessage {
    
    private final UUID maidUUID;
    private final Map<String, String> spellMap;
    
    /**
     * 构造函数
     * @param maidUUID 女仆UUID
     * @param spellMap 法术映射 <模组ID, 法术名称>
     */
    public SpellSyncMessage(UUID maidUUID, Map<String, String> spellMap) {
        this.maidUUID = maidUUID;
        this.spellMap = spellMap != null ? spellMap : new HashMap<>();
    }
    
    /**
     * 编码消息到网络缓冲区
     */
    public static void encode(SpellSyncMessage message, FriendlyByteBuf buf) {
        // 写入女仆UUID
        buf.writeUUID(message.maidUUID);
        
        // 写入法术数量
        buf.writeInt(message.spellMap.size());
        
        // 写入每个法术
        for (Map.Entry<String, String> entry : message.spellMap.entrySet()) {
            buf.writeUtf(entry.getKey());   // 模组ID
            buf.writeUtf(entry.getValue()); // 法术名称
        }
    }
    
    /**
     * 从网络缓冲区解码消息
     */
    public static SpellSyncMessage decode(FriendlyByteBuf buf) {
        UUID maidUUID = buf.readUUID();
        int spellCount = buf.readInt();
        
        Map<String, String> spellMap = new HashMap<>();
        for (int i = 0; i < spellCount; i++) {
            String modId = buf.readUtf();
            String spellName = buf.readUtf();
            spellMap.put(modId, spellName);
        }
        
        return new SpellSyncMessage(maidUUID, spellMap);
    }
    
    /**
     * 处理消息
     */
    public static void handle(SpellSyncMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
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
    private static void handleClientSide(SpellSyncMessage message) {
        SpellSyncClientHandler.handleSpellSync(message.maidUUID, message.spellMap);
    }
    
    /**
     * 获取女仆UUID
     */
    public UUID getMaidUUID() {
        return maidUUID;
    }
    
    /**
     * 获取法术映射
     */
    public Map<String, String> getSpellMap() {
        return spellMap;
    }
}

