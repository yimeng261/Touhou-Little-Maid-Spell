package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.message.TravelerTitlesStructureMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 同步玩家进入了哪个支持 Traveler's Titles 标题的结构。 */
public final class TravelerTitlesStructureEvents {
    private static final List<ResourceLocation> TITLED_STRUCTURES = List.of(
            id("relic_sanctum"),
            id("fallen_sanctum"),
            id("stellar_endshore"),
            id("starfall_garden"),
            id("hidden_retreat")
    );

    /** 结构查询按间隔进行，进入结构后的标题最多延后这么多 tick。 */
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final Map<UUID, ResourceLocation> LAST_STRUCTURES = new HashMap<>();

    private TravelerTitlesStructureEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        ResourceLocation current = findStructure(player);
        UUID playerId = player.getUUID();
        ResourceLocation previous = LAST_STRUCTURES.put(playerId, current);
        if (!Objects.equals(previous, current)) {
            PacketDistributor.sendToPlayer(player, new TravelerTitlesStructureMessage(current));
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
        return ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, path);
    }
}
