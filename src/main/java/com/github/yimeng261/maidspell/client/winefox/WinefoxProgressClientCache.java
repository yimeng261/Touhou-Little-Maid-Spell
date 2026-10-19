package com.github.yimeng261.maidspell.client.winefox;

/** 客户端缓存的本地玩家誓约进度，供归星的工具提示使用。 */
public final class WinefoxProgressClientCache {
    private static int wins;
    private static boolean soloWin;
    private static boolean known;

    private WinefoxProgressClientCache() {
    }

    public static void update(int wins, boolean soloWin) {
        WinefoxProgressClientCache.wins = wins;
        WinefoxProgressClientCache.soloWin = soloWin;
        WinefoxProgressClientCache.known = true;
    }

    public static int getWins() {
        return wins;
    }

    public static boolean hasSoloWin() {
        return soloWin;
    }

    public static boolean isKnown() {
        return known;
    }

    public static void clear() {
        wins = 0;
        soloWin = false;
        known = false;
    }
}
