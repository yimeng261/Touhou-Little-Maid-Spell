package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogNecklace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 幻化项链样式切换消息
 */
public class TransmogNecklaceMessage {
    private final int inventorySlot;
    private final int styleIndex;

    public TransmogNecklaceMessage(int inventorySlot, int styleIndex) {
        this.inventorySlot = inventorySlot;
        this.styleIndex = styleIndex;
    }

    public static void encode(TransmogNecklaceMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.inventorySlot);
        buf.writeInt(message.styleIndex);
    }

    public static TransmogNecklaceMessage decode(FriendlyByteBuf buf) {
        return new TransmogNecklaceMessage(buf.readInt(), buf.readInt());
    }

    public static void handle(TransmogNecklaceMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(message, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void handleServer(TransmogNecklaceMessage message, ServerPlayer player) {
        if (player == null) {
            return;
        }

        ItemStack stack = getInventoryStack(player.getInventory(), message.inventorySlot);
        if (stack.isEmpty() || !stack.is(MaidSpellItems.TRANSMOG_NECKLACE.get())) {
            return;
        }

        TransmogNecklace.setSelectedStyle(stack, message.styleIndex);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static ItemStack getInventoryStack(Inventory inventory, int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot < inventory.items.size()) {
            return inventory.items.get(inventorySlot);
        }
        if (inventorySlot >= 36 && inventorySlot < 36 + inventory.armor.size()) {
            return inventory.armor.get(inventorySlot - 36);
        }
        if (inventorySlot == 40 && !inventory.offhand.isEmpty()) {
            return inventory.offhand.get(0);
        }
        return ItemStack.EMPTY;
    }
}
