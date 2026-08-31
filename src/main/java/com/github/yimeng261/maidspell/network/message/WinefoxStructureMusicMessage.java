package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Syncs whether the local player is inside the Stellar Endshore structure. */
public record WinefoxStructureMusicMessage(boolean insideStructure) implements CustomPacketPayload {
    public static final Type<WinefoxStructureMusicMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "winefox_structure_music"));

    public static final StreamCodec<ByteBuf, WinefoxStructureMusicMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            WinefoxStructureMusicMessage::insideStructure,
            WinefoxStructureMusicMessage::new
    );

    @Override
    public Type<WinefoxStructureMusicMessage> type() {
        return TYPE;
    }
}
