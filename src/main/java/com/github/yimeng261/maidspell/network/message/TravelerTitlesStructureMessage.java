package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.compat.travelerstitles.client.TravelerTitlesStructureClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Syncs the local player's current Traveler's Titles structure, or null when outside one. */
public final class TravelerTitlesStructureMessage {
    @Nullable
    private final ResourceLocation structureId;

    public TravelerTitlesStructureMessage(@Nullable ResourceLocation structureId) {
        this.structureId = structureId;
    }

    public static void encode(TravelerTitlesStructureMessage message, FriendlyByteBuf buf) {
        buf.writeBoolean(message.structureId != null);
        if (message.structureId != null) {
            buf.writeResourceLocation(message.structureId);
        }
    }

    public static TravelerTitlesStructureMessage decode(FriendlyByteBuf buf) {
        return new TravelerTitlesStructureMessage(
                buf.readBoolean() ? buf.readResourceLocation() : null
        );
    }

    public static void handle(TravelerTitlesStructureMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> TravelerTitlesStructureClient.setStructure(message.structureId)
        ));
        context.setPacketHandled(true);
    }
}
