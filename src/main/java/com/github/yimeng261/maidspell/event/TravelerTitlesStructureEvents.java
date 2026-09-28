package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.TravelerTitlesStructureMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Synchronizes entry into the structures that have Traveler's Titles support. */
public final class TravelerTitlesStructureEvents {
    private static final List<ResourceLocation> TITLED_STRUCTURES = List.of(
            id("relic_sanctum"),
            id("fallen_sanctum"),
            id("stellar_endshore"),
            id("starfall_garden"),
            id("hidden_retreat")
    );

    private static final Map<UUID, ResourceLocation> LAST_STRUCTURES = new HashMap<>();

    private TravelerTitlesStructureEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation current = findStructure(player);
        UUID playerId = player.getUUID();
        ResourceLocation previous = LAST_STRUCTURES.put(playerId, current);
        if (!Objects.equals(previous, current)) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new TravelerTitlesStructureMessage(current)
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_STRUCTURES.remove(event.getEntity().getUUID());
    }

    private static ResourceLocation findStructure(ServerPlayer player) {
        var registry = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structureManager = player.serverLevel().structureManager();

        for (ResourceLocation id : TITLED_STRUCTURES) {
            Structure structure = registry.get(id);
            if (structure != null && structureManager
                    .getStructureWithPieceAt(player.blockPosition(), structure)
                    .isValid()) {
                return id;
            }
        }
        return null;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MaidSpellMod.MOD_ID, path);
    }
}
