package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 同步本地玩家的星之魔女誓约进度：常规胜利次数与是否完成专属胜利。 */
public record WinefoxProgressMessage(int wins, boolean soloWin) implements CustomPacketPayload {
    public static final Type<WinefoxProgressMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "winefox_progress"));

    public static final StreamCodec<ByteBuf, WinefoxProgressMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            WinefoxProgressMessage::wins,
            ByteBufCodecs.BOOL,
            WinefoxProgressMessage::soloWin,
            WinefoxProgressMessage::new
    );

    @Override
    public Type<WinefoxProgressMessage> type() {
        return TYPE;
    }
}
