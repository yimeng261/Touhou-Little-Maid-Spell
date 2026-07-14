package com.github.yimeng261.maidspell.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Prepares a detached client maid before TLM constructs a remote maid menu. */
public record EnderPocketMaidSnapshotMessage(UUID sessionId, boolean acknowledge,
                                             MaidEntityRestoreMessage snapshot) {
    public static void encode(EnderPocketMaidSnapshotMessage message, FriendlyByteBuf buf) {
        buf.writeUUID(message.sessionId);
        buf.writeBoolean(message.acknowledge);
        MaidEntityRestoreMessage.encode(message.snapshot, buf);
    }

    public static EnderPocketMaidSnapshotMessage decode(FriendlyByteBuf buf) {
        return new EnderPocketMaidSnapshotMessage(
                buf.readUUID(),
                buf.readBoolean(),
                MaidEntityRestoreMessage.decode(buf)
        );
    }

    public static void handle(EnderPocketMaidSnapshotMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> EnderPocketMaidSnapshotClientHandler.handle(message)
        ));
        context.setPacketHandled(true);
    }
}
