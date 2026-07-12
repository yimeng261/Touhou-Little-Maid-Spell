package com.github.yimeng261.maidspell.spell.manager;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes scoreboard teams persisted by versions that represented maid alliances as vanilla teams.
 */
public final class AllianceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LEGACY_TEAM_PREFIX = "maidspell_alliance_";

    private AllianceManager() {
    }

    public static void cleanupLegacyTeams(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        List<PlayerTeam> legacyTeams = new ArrayList<>();
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            if (team.getName().startsWith(LEGACY_TEAM_PREFIX)) {
                legacyTeams.add(team);
            }
        }
        legacyTeams.forEach(scoreboard::removePlayerTeam);
        if (!legacyTeams.isEmpty()) {
            LOGGER.info("Removed {} legacy MaidSpell alliance scoreboard teams", legacyTeams.size());
        }
    }
}
