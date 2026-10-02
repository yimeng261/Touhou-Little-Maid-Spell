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
 * 打开女仆物品栏
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-10-25 20:59
 */
public record C2SEnderPocketOpenInventory(UUID maidUuid) implements CustomPacketPayload {
    public static final Type<C2SEnderPocketOpenInventory> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_open_maid_inventory"));

    public static final StreamCodec<ByteBuf, C2SEnderPocketOpenInventory> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            C2SEnderPocketOpenInventory::maidUuid,
            C2SEnderPocketOpenInventory::new
    );

    @Override
    public Type<C2SEnderPocketOpenInventory> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        if (!EnderPocketRequestRateLimiter.tryAcquire(player, EnderPocketRequestRateLimiter.Action.OPEN)) {
            return;
        }
        EnderPocketService.openMaidInventory(player, maidUuid());
    }
}
