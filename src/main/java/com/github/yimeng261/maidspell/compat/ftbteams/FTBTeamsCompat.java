package com.github.yimeng261.maidspell.compat.ftbteams;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
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

    public static boolean areFriendly(@Nullable Entity first, @Nullable Entity second) {
        if (!isLoaded() || first == null || second == null) {
            return false;
        }
        return areFriendly(MaidSpellAllyResolver.collectAffinityIds(first), MaidSpellAllyResolver.collectAffinityIds(second));
    }

    public static boolean areFriendly(Set<UUID> firstIds, Set<UUID> secondIds) {
        return isLoaded() && ApiBridge.areFriendly(firstIds, secondIds);
    }

    private static final class ApiBridge {

        private static boolean areFriendly(Set<UUID> firstIds, Set<UUID> secondIds) {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (api == null || !api.isManagerLoaded()) {
                return false;
            }

            TeamManager manager = api.getManager();
            for (UUID firstId : firstIds) {
                for (UUID secondId : secondIds) {
                    if (manager.arePlayersInSameTeam(firstId, secondId)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
