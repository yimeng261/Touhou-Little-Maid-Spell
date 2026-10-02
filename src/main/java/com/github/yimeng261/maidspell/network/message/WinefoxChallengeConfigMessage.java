package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfigMenu;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 星芒短剑挑战配置界面的保存请求（客户端 → 服务端）。 */
public record WinefoxChallengeConfigMessage(int itemSlot, WinefoxChallengeConfig config,
                                            List<WinefoxChallengeConfig> presets) implements CustomPacketPayload {
    public static final Type<WinefoxChallengeConfigMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "winefox_challenge_config"));

    public static final StreamCodec<FriendlyByteBuf, WinefoxChallengeConfigMessage> STREAM_CODEC =
            StreamCodec.ofMember(WinefoxChallengeConfigMessage::encode, WinefoxChallengeConfigMessage::decode);

    @Override
    public Type<WinefoxChallengeConfigMessage> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(itemSlot);
        config.writeToBuffer(buffer);
        WinefoxChallengePresets.writeBuffer(buffer, presets);
    }

    public static WinefoxChallengeConfigMessage decode(FriendlyByteBuf buffer) {
        return new WinefoxChallengeConfigMessage(buffer.readInt(), WinefoxChallengeConfig.fromBuffer(buffer),
                WinefoxChallengePresets.readBuffer(buffer));
    }

    public void handle(ServerPlayer player) {
        if (!(player.containerMenu instanceof WinefoxChallengeConfigMenu menu)
                || menu.getItemSlot() != itemSlot || !menu.stillValid(player)) {
            return;
        }
        ItemStack stack = stackAt(player, itemSlot);
        if (stack.is(MaidSpellItems.STARGLINT_DAGGER.get())) {
            WinefoxChallengeConfig.writeToItem(stack, config);
            WinefoxChallengePresets.write(stack, presets);
            player.getInventory().setChanged();
        }
    }

    private static ItemStack stackAt(ServerPlayer player, int slot) {
        if (slot == 40) {
            return player.getInventory().offhand.get(0);
        }
        return slot >= 0 && slot < player.getInventory().items.size()
                ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
    }
}
