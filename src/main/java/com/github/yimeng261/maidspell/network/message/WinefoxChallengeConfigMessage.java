package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfigMenu;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server save action for the Starglint Dagger challenge screen. */
public record WinefoxChallengeConfigMessage(int itemSlot, WinefoxChallengeConfig config,
                                            java.util.List<WinefoxChallengeConfig> presets) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(itemSlot);
        config.writeToBuffer(buffer);
        WinefoxChallengePresets.writeBuffer(buffer, presets);
    }

    public static WinefoxChallengeConfigMessage decode(FriendlyByteBuf buffer) {
        return new WinefoxChallengeConfigMessage(buffer.readInt(), WinefoxChallengeConfig.fromBuffer(buffer),
                WinefoxChallengePresets.readBuffer(buffer));
    }

    public static void handle(WinefoxChallengeConfigMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof WinefoxChallengeConfigMenu menu)
                    || menu.getItemSlot() != message.itemSlot() || !menu.stillValid(player)) {
                return;
            }
            ItemStack stack = stackAt(player, message.itemSlot());
            if (stack.is(MaidSpellItems.STARGLINT_DAGGER.get())) {
                WinefoxChallengeConfig.writeToItem(stack, message.config());
                WinefoxChallengePresets.write(stack, message.presets());
                player.getInventory().setChanged();
            }
        });
        context.setPacketHandled(true);
    }

    private static ItemStack stackAt(ServerPlayer player, int slot) {
        if (slot == 40) {
            return player.getInventory().offhand.get(0);
        }
        return slot >= 0 && slot < player.getInventory().items.size()
                ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
    }
}
