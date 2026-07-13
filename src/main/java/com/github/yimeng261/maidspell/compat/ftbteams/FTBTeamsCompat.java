package com.github.yimeng261.maidspell.compat.ftbteams;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.property.BooleanProperty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

public final class FTBTeamsCompat {
    public static final String MOD_ID = "ftbteams";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private FTBTeamsCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void init() {
        if (isLoaded()) {
            ApiBridge.init();
        }
    }

    public static boolean areFriendly(@Nullable Entity source, @Nullable Entity target) {
        if (!isLoaded() || source == null || target == null) {
            return false;
        }
        return areFriendly(source, MaidSpellAllyResolver.collectAffinityIds(source),
                MaidSpellAllyResolver.collectAffinityIds(target));
    }

    public static boolean areFriendly(Entity source, Set<UUID> sourceIds, Set<UUID> targetIds) {
        if (!isLoaded()) {
            return false;
        }
        MaidSpellAllyResolver.AffinityType sourceType = MaidSpellAllyResolver.getAffinityType(source);
        return sourceType != MaidSpellAllyResolver.AffinityType.NONE
                && ApiBridge.shouldPreventFriendlyFire(sourceIds, targetIds, sourceType);
    }

    private static final class ApiBridge {
        private static final BooleanProperty ALLOW_PLAYER_FRIENDLY_FIRE = new BooleanProperty(
                new ResourceLocation(MaidSpellMod.MOD_ID, "allow_player_friendly_fire"), true);
        private static final BooleanProperty ALLOW_MAID_FRIENDLY_FIRE = new BooleanProperty(
                new ResourceLocation(MaidSpellMod.MOD_ID, "allow_maid_friendly_fire"), false);

        private ApiBridge() {
        }

        private static void init() {
            TeamEvent.COLLECT_PROPERTIES.register(event -> {
                event.add(ALLOW_PLAYER_FRIENDLY_FIRE);
                event.add(ALLOW_MAID_FRIENDLY_FIRE);
            });
        }

        private static boolean shouldPreventFriendlyFire(Set<UUID> sourceIds, Set<UUID> targetIds,
                                                         MaidSpellAllyResolver.AffinityType sourceType) {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (api == null || !api.isManagerLoaded()) {
                return false;
            }

            TeamManager manager = api.getManager();
            for (UUID sourceId : sourceIds) {
                for (UUID targetId : targetIds) {
                    if (manager.arePlayersInSameTeam(sourceId, targetId)) {
                        return manager.getTeamForPlayerID(sourceId)
                                .map(team -> isFriendlyFireDisabled(team, sourceType))
                                .orElse(false);
                    }
                }
            }
            return false;
        }

        private static boolean isFriendlyFireDisabled(Team team, MaidSpellAllyResolver.AffinityType sourceType) {
            return switch (sourceType) {
                case PLAYER -> !team.getProperty(ALLOW_PLAYER_FRIENDLY_FIRE);
                case MAID -> !team.getProperty(ALLOW_MAID_FRIENDLY_FIRE);
                case NONE -> false;
            };
        }
    }
}
