package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 请求女仆列表
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-10-25 20:29
 */
public record C2SEnderPocketMaidList(boolean fromMaidBackpack) implements CustomPacketPayload {
    public static final Type<C2SEnderPocketMaidList> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_request_maid_list"));

    public static final StreamCodec<ByteBuf, C2SEnderPocketMaidList> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            C2SEnderPocketMaidList::fromMaidBackpack,
            C2SEnderPocketMaidList::new
    );

    @Override
    public Type<C2SEnderPocketMaidList> type() {
        return TYPE;
    }
}
