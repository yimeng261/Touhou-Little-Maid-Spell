package com.github.yimeng261.maidspell.winefox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Persistent per-player progression for the Stellar Witch challenge tools. */
public final class WinefoxChallengeProgress {
    public static final String DEFEATED_TAG = "MaidSpellWinefoxDefeated";
    public static final String ACTIVE_TAG = "MaidSpellWinefoxChallengeActive";
    public static final String WINS_TAG = "MaidSpellWinefoxWins";
    public static final String SOLO_TAG = "MaidSpellWinefoxSoloWin";

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

    public static int getWins(Player player) {
        return player == null ? 0 : player.getPersistentData().getInt(WINS_TAG);
    }

    public static boolean hasSoloWin(Player player) {
        return player != null && player.getPersistentData().getBoolean(SOLO_TAG);
    }

    public static void setProgress(Player player, int wins, boolean soloWin) {
        if (player == null) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putInt(WINS_TAG, Math.max(0, wins));
        data.putBoolean(SOLO_TAG, soloWin);
    }

    public static void copy(Player from, Player to) {
        if (from == null || to == null) {
            return;
        }
        CompoundTag source = from.getPersistentData();
        CompoundTag target = to.getPersistentData();
        if (source.contains(WINS_TAG)) {
            target.putInt(WINS_TAG, source.getInt(WINS_TAG));
        }
        if (source.contains(SOLO_TAG)) {
            target.putBoolean(SOLO_TAG, source.getBoolean(SOLO_TAG));
        }
        if (source.getBoolean(DEFEATED_TAG)) {
            target.putBoolean(DEFEATED_TAG, true);
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
