package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Confirms that the client proxy exists before the server opens the native TLM menu. */
public record EnderPocketMaidReadyMessage(UUID sessionId) {
    public static void encode(EnderPocketMaidReadyMessage message, FriendlyByteBuf buf) {
        buf.writeUUID(message.sessionId);
    }

    public static EnderPocketMaidReadyMessage decode(FriendlyByteBuf buf) {
        return new EnderPocketMaidReadyMessage(buf.readUUID());
    }

    public static void handle(EnderPocketMaidReadyMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> EnderPocketService.completeRemoteOpen(
                context.getSender(), message.sessionId()));
        context.setPacketHandled(true);
    }
}
