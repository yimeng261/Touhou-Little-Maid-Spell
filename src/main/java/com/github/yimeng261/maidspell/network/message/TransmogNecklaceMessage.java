package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogNecklace;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record TransmogNecklaceMessage(int inventorySlot, int styleIndex) implements CustomPacketPayload {
    public static final Type<TransmogNecklaceMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "transmog_necklace_style"));

    public static final StreamCodec<ByteBuf, TransmogNecklaceMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            TransmogNecklaceMessage::inventorySlot,
            ByteBufCodecs.INT,
            TransmogNecklaceMessage::styleIndex,
            TransmogNecklaceMessage::new
    );

    @Override
    public Type<TransmogNecklaceMessage> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (inventorySlot == 40) {
            stack = player.getOffhandItem();
        }
        if (stack.is(MaidSpellItems.TRANSMOG_NECKLACE.get())) {
            TransmogNecklace.setSelectedStyle(stack, styleIndex);
            if (inventorySlot == 40) {
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack);
            } else {
                player.getInventory().setItem(inventorySlot, stack);
            }
            player.containerMenu.broadcastChanges();
        }
    }
}
