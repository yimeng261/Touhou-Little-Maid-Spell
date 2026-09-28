package com.github.yimeng261.maidspell.winefox;

import net.minecraft.world.entity.player.Player;

/** Persistent per-player progression for the Stellar Witch challenge tools. */
public final class WinefoxChallengeProgress {
    public static final String DEFEATED_TAG = "MaidSpellWinefoxDefeated";
    public static final String ACTIVE_TAG = "MaidSpellWinefoxChallengeActive";

    private WinefoxChallengeProgress() {
    }

    public static boolean hasDefeated(Player player) {
        return player != null && player.getPersistentData().getBoolean(DEFEATED_TAG);
    }

    public static void markDefeated(Player player) {
        if (player != null) {
            player.getPersistentData().putBoolean(DEFEATED_TAG, true);
        }
    }

    public static boolean hasActiveChallenge(Player player) {
        return player != null && player.getPersistentData().getBoolean(ACTIVE_TAG);
    }

    public static void markChallengeActive(Player player) {
        if (player != null) {
            player.getPersistentData().putBoolean(ACTIVE_TAG, true);
        }
    }

    public static void clearChallengeActive(Player player) {
        if (player != null) {
            player.getPersistentData().remove(ACTIVE_TAG);
        }
    }

}
