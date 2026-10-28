package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 每场遭遇中对隐身的反应，以及搜寻某位玩家所花的时间。 */
final class WinefoxInvisibilityTracker {
    record Reaction(boolean firstInvisibility, boolean foundInvisiblePlayer) {}

    final Set<UUID> noticedPlayers = new HashSet<>();
    final Set<UUID> foundPlayers = new HashSet<>();
    UUID searchingPlayer;
    int remainingTicks;

    void tick() {
        if (this.remainingTicks > 0 && --this.remainingTicks == 0) {
            this.searchingPlayer = null;
        }
    }

    Reaction observe(UUID player, boolean invisible, boolean detectable, int graceTicks) {
        if (!invisible) return new Reaction(false, false);
        this.searchingPlayer = player;
        this.remainingTicks = Math.max(1, graceTicks);
        return new Reaction(this.noticedPlayers.add(player),
                detectable && this.foundPlayers.add(player));
    }

    boolean allows(UUID candidate) {
        return this.remainingTicks <= 0
            || this.searchingPlayer == null
            || this.searchingPlayer.equals(candidate);
    }

    static double detectionRange(double followRange, double visibility) {
        return Math.max(2.0D, followRange * visibility);
    }

    void reset() {
        this.noticedPlayers.clear();
        this.foundPlayers.clear();
        this.searchingPlayer = null;
        this.remainingTicks = 0;
    }
}
