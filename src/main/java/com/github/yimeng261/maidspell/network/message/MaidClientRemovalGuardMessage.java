package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record MaidClientRemovalGuardMessage(int entityId, boolean allowRemoval) implements CustomPacketPayload {
    public static final Type<MaidClientRemovalGuardMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid_client_removal_guard"));

    public static final StreamCodec<ByteBuf, MaidClientRemovalGuardMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MaidClientRemovalGuardMessage::entityId,
            ByteBufCodecs.BOOL,
            MaidClientRemovalGuardMessage::allowRemoval,
            MaidClientRemovalGuardMessage::new
    );

    @Override
    public Type<MaidClientRemovalGuardMessage> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        if (allowRemoval) {
            ClientMaidRemovalGuard.allowRemoval(entityId);
        } else {
            ClientMaidRemovalGuard.markProtected(entityId);
        }
    }
}
