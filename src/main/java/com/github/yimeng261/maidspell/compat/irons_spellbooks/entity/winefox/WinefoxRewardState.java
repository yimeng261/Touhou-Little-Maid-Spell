package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

public enum WinefoxRewardState {
    NONE,
    GRANTING,
    GRANTED,
    NOT_ELIGIBLE;

    public static WinefoxRewardState fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NOT_ELIGIBLE;
    }
}
