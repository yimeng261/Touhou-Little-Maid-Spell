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
 * 传送到佩戴锚定核心的女仆身边
 */
public record C2SEnderPocketTeleport(UUID maidUuid) implements CustomPacketPayload {
    public static final Type<C2SEnderPocketTeleport> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_teleport_to_maid"));

    public static final StreamCodec<ByteBuf, C2SEnderPocketTeleport> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            C2SEnderPocketTeleport::maidUuid,
            C2SEnderPocketTeleport::new
    );

    @Override
    public Type<C2SEnderPocketTeleport> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        if (!EnderPocketRequestRateLimiter.tryAcquire(player, EnderPocketRequestRateLimiter.Action.TELEPORT)) {
            return;
        }
        EnderPocketService.teleportToMaid(player, maidUuid());
    }
}
