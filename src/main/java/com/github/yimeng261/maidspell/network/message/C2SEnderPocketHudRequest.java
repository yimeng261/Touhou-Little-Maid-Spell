package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 请求末影腰包女仆状态栏数据
 */
public record C2SEnderPocketHudRequest() implements CustomPacketPayload {
    public static final C2SEnderPocketHudRequest INSTANCE = new C2SEnderPocketHudRequest();

    public static final Type<C2SEnderPocketHudRequest> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_request_hud_data"));

    public static final StreamCodec<ByteBuf, C2SEnderPocketHudRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<C2SEnderPocketHudRequest> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        if (!EnderPocketRequestRateLimiter.tryAcquire(player, EnderPocketRequestRateLimiter.Action.HUD)) {
            return;
        }
        player.connection.send(new S2CEnderPocketHudUpdate(EnderPocketService.getPlayerEnderPocketMaids(player)));
    }
}
