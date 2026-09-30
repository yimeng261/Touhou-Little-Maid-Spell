package com.github.yimeng261.maidspell.winefox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/** Persistent per-player progression for the Stellar Witch challenge tools. */
public final class WinefoxChallengeProgress {
    public static final String DEFEATED_TAG = "MaidSpellWinefoxDefeated";
    public static final String ACTIVE_TAG = "MaidSpellWinefoxChallengeActive";

    private WinefoxChallengeProgress() {
    }

    /** 已击败标记放在 PlayerPersisted 里，死亡重生和离开末地时随玩家数据复制；旧存档写在外层的标记读到时搬进来。 */
    public static boolean hasDefeated(Player player) {
        if (player == null) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(DEFEATED_TAG)) {
            data.remove(DEFEATED_TAG);
            markDefeated(player);
            return true;
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG).getBoolean(DEFEATED_TAG);
    }

    public static void markDefeated(Player player) {
        if (player != null) {
            persisted(player).putBoolean(DEFEATED_TAG, true);
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

    private static CompoundTag persisted(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
