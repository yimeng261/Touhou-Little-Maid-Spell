package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 客户端确认女仆代理已就绪，服务端随后打开 TLM 原生界面
 */
public record C2SEnderPocketMaidReady(UUID sessionId) implements CustomPacketPayload {
    public static final Type<C2SEnderPocketMaidReady> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_maid_ready"));

    public static final StreamCodec<ByteBuf, C2SEnderPocketMaidReady> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            C2SEnderPocketMaidReady::sessionId,
            C2SEnderPocketMaidReady::new
    );

    @Override
    public Type<C2SEnderPocketMaidReady> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        EnderPocketService.completeRemoteOpen(player, sessionId());
    }
}
