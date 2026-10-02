package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

/**
 * 在 TLM 构建远程女仆界面前，让客户端准备一个独立的女仆代理
 */
public record S2CEnderPocketMaidSnapshot(UUID sessionId, boolean acknowledge,
                                         MaidEntityRestoreMessage snapshot) implements CustomPacketPayload {
    public static final Type<S2CEnderPocketMaidSnapshot> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_maid_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CEnderPocketMaidSnapshot> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            S2CEnderPocketMaidSnapshot::sessionId,
            ByteBufCodecs.BOOL,
            S2CEnderPocketMaidSnapshot::acknowledge,
            MaidEntityRestoreMessage.STREAM_CODEC,
            S2CEnderPocketMaidSnapshot::snapshot,
            S2CEnderPocketMaidSnapshot::new
    );

    @Override
    public Type<S2CEnderPocketMaidSnapshot> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        EnderPocketMaidSnapshotClientHandler.handle(this);
    }
}
