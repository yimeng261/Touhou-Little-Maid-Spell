package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.WinefoxStructureMusicMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends structure membership to the client that owns the local music controller. */
public final class WinefoxStructureMusicEvents {
    private static final ResourceKey<Structure> STELLAR_ENDSHORE = ResourceKey.create(
            Registries.STRUCTURE, new ResourceLocation(MaidSpellMod.MOD_ID, "stellar_endshore"));
    private static final Map<UUID, Boolean> LAST_STATE = new HashMap<>();

    private WinefoxStructureMusicEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        boolean inside = isInsideStellarEndshore(player);
        UUID playerId = player.getUUID();
        Boolean previous = LAST_STATE.put(playerId, inside);
        if (previous == null || previous != inside) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new WinefoxStructureMusicMessage(inside));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_STATE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_STATE.clear();
    }

    private static boolean isInsideStellarEndshore(ServerPlayer player) {
        var registry = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = registry.get(STELLAR_ENDSHORE);
        return structure != null
                && player.serverLevel().structureManager()
                .getStructureWithPieceAt(player.blockPosition(), structure)
                .isValid();
    }
}
