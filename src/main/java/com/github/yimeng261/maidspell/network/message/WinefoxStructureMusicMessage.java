package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.WinefoxSeatedAmbienceController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Syncs whether the local player is inside the Stellar Endshore structure. */
public final class WinefoxStructureMusicMessage {
    private final boolean insideStructure;

    public WinefoxStructureMusicMessage(boolean insideStructure) {
        this.insideStructure = insideStructure;
    }

    public static void encode(WinefoxStructureMusicMessage message, FriendlyByteBuf buf) {
        buf.writeBoolean(message.insideStructure);
    }

    public static WinefoxStructureMusicMessage decode(FriendlyByteBuf buf) {
        return new WinefoxStructureMusicMessage(buf.readBoolean());
    }

    public static void handle(WinefoxStructureMusicMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WinefoxSeatedAmbienceController.setInsideStellarEndshore(message.insideStructure))
        );
        context.setPacketHandled(true);
    }
}
