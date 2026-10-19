package com.github.yimeng261.maidspell.winefox;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

public final class WinefoxVowProgress {
    public static final int REQUIRED_WINS = 2;

    private WinefoxVowProgress() {
    }

    public static void recordVictory(Player player, boolean soloConditionMet) {
        if (player == null) {
            return;
        }
        int wins = WinefoxChallengeProgress.getWins(player) + 1;
        boolean soloWin = WinefoxChallengeProgress.hasSoloWin(player)
            || (soloConditionMet && wins >= REQUIRED_WINS);
        WinefoxChallengeProgress.setProgress(player, wins, soloWin);
    }

    public static void migrateLegacy(Player player, int legacyWins, boolean legacySoloWin) {
        if (player == null
            || WinefoxChallengeProgress.getWins(player) > 0
            || WinefoxChallengeProgress.hasSoloWin(player)) {
            return;
        }
        if (legacyWins <= 0 && !legacySoloWin) {
            return;
        }
        WinefoxChallengeProgress.setProgress(player, Math.max(legacyWins, 0), legacySoloWin);
    }

    public static boolean isReady(Player player) {
        return player != null
            && WinefoxChallengeProgress.getWins(player) >= REQUIRED_WINS
            && WinefoxChallengeProgress.hasSoloWin(player);
    }

    public static int displayWins(int wins) {
        return Math.max(0, Math.min(wins, REQUIRED_WINS));
    }

    public static MutableComponent progress(int wins) {
        return Component.translatable(
            "item.touhou_little_maid_spell.returning_star.progress", displayWins(wins));
    }

    public static MutableComponent nextStep(int wins, boolean soloWin) {
        if (wins >= REQUIRED_WINS && soloWin) {
            return Component.translatable("item.touhou_little_maid_spell.returning_star.ready");
        }
        if (wins >= REQUIRED_WINS) {
            return Component.translatable("item.touhou_little_maid_spell.returning_star.awaiting_dance");
        }
        return null;
    }

    public static MutableComponent actionBar(int wins, boolean soloWin, boolean counted) {
        if (!counted) {
            return Component.translatable(
                "item.touhou_little_maid_spell.returning_star.not_counted", displayWins(wins));
        }
        MutableComponent next = nextStep(wins, soloWin);
        return next == null ? progress(wins) : next;
    }
}
