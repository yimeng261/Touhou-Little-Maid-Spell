package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.client.winefox.WinefoxProgressClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WinefoxProgressMessage {
    private final int wins;
    private final boolean soloWin;

    public WinefoxProgressMessage(int wins, boolean soloWin) {
        this.wins = wins;
        this.soloWin = soloWin;
    }

    public static void encode(WinefoxProgressMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.wins);
        buf.writeBoolean(message.soloWin);
    }

    public static WinefoxProgressMessage decode(FriendlyByteBuf buf) {
        return new WinefoxProgressMessage(buf.readInt(), buf.readBoolean());
    }

    public static void handle(WinefoxProgressMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> WinefoxProgressClientCache.update(message.wins, message.soloWin)
        ));
        context.setPacketHandled(true);
    }
}
