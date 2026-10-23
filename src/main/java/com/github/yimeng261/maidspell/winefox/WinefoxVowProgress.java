package com.github.yimeng261.maidspell.winefox;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 「归星」誓约的玩家级进度：先两场常规胜利，再一场女仆不造成伤害的专属胜利。
 * 进度按玩家 UUID 记在 {@link WinefoxChallengeProgress}，提示文本同时供服务端动作栏和客户端工具提示使用。
 */
public final class WinefoxVowProgress {
    public static final int REQUIRED_WINS = 2;

    private WinefoxVowProgress() {
    }

    public static void recordVictory(MinecraftServer server, UUID playerId, boolean soloConditionMet) {
        int wins = WinefoxChallengeProgress.getWins(server, playerId) + 1;
        boolean soloWin = WinefoxChallengeProgress.hasSoloWin(server, playerId)
            || (soloConditionMet && wins >= REQUIRED_WINS);
        WinefoxChallengeProgress.setProgress(server, playerId, wins, soloWin);
    }

    /** 把魔女实体上的旧版计数搬给玩家；玩家已有进度时不覆盖。 */
    public static void migrateLegacy(MinecraftServer server, UUID playerId, int legacyWins, boolean legacySoloWin) {
        if (WinefoxChallengeProgress.getWins(server, playerId) > 0
            || WinefoxChallengeProgress.hasSoloWin(server, playerId)) {
            return;
        }
        if (legacyWins <= 0 && !legacySoloWin) {
            return;
        }
        WinefoxChallengeProgress.setProgress(server, playerId, Math.max(legacyWins, 0), legacySoloWin);
    }

    public static boolean isReady(MinecraftServer server, UUID playerId) {
        return WinefoxChallengeProgress.getWins(server, playerId) >= REQUIRED_WINS
            && WinefoxChallengeProgress.hasSoloWin(server, playerId);
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
