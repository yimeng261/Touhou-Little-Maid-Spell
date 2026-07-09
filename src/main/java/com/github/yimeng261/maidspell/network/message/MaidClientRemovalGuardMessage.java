package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MaidClientRemovalGuardMessage {
    private final int entityId;
    private final boolean allowRemoval;

    public MaidClientRemovalGuardMessage(int entityId, boolean allowRemoval) {
        this.entityId = entityId;
        this.allowRemoval = allowRemoval;
    }

    public static void encode(MaidClientRemovalGuardMessage message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
        buf.writeBoolean(message.allowRemoval);
    }

    public static MaidClientRemovalGuardMessage decode(FriendlyByteBuf buf) {
        return new MaidClientRemovalGuardMessage(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(MaidClientRemovalGuardMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (message.allowRemoval) {
                        ClientMaidRemovalGuard.allowRemovalNow(message.entityId);
                    } else {
                        ClientMaidRemovalGuard.markProtected(message.entityId);
                    }
                });
            }
        });
        context.setPacketHandled(true);
    }
}
