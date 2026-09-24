package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/**
 * 星之魔女遭遇的互斥状态。
 *
 * <p>状态转移是这套生命周期的唯一裁决者：业务代码只调 {@code maidspell$setEncounterState}，
 * 由这里决定是否合法。非法转移一律拒绝并记 WARN，不允许产生"半完成"状态。
 *
 * <p><b>自转移不是合法转移。</b>把"已经在目标状态"当成成功会让状态机失去约束力，
 * 也无法在日志里区分"重复请求"与"非法请求"。需要容错的地方（管理员修复命令）由调用方
 * 先判断当前状态，而不是让状态机吞掉重复写入。
 */
public enum WinefoxEncounterState {
    SEATED,
    CHALLENGE_START,
    COMBAT,
    DEFEATED,
    RETURNING;

    public boolean canTransitionTo(WinefoxEncounterState next) {
        if (this == next) {
            return false;
        }
        return switch (this) {
            case SEATED ->
                // 邀战开始，或普通生物直接把她拖进战斗（跳开场）。
                next == CHALLENGE_START || next == COMBAT;
            case CHALLENGE_START ->
                // 开场走完进战斗；挑战者在开场期间离场要退回坐姿
                // （见 tickChallengeStart），异常结束则直接进回归。
                next == COMBAT || next == SEATED || next == RETURNING;
            case COMBAT -> next == DEFEATED || next == RETURNING;
            case DEFEATED -> next == RETURNING;
            case RETURNING -> next == SEATED;
        };
    }

    public static WinefoxEncounterState fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : SEATED;
    }
}
