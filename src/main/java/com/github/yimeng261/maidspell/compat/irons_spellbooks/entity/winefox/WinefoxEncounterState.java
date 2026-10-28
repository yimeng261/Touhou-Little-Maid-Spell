package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/**
 * 星之魔女遭遇的互斥状态。
 *
 * <p>转移表是唯一裁决者：业务代码只调 {@code maidspell$setEncounterState}，非法转移一律拒绝并
 * 记 WARN，不产生"半完成"状态。
 *
 * <p>自转移不算合法：把"已经在目标状态"当成成功会让状态机失去约束力，也分不清"重复请求"与
 * "非法请求"。需要容错的地方（管理员修复命令）由调用方先判断当前状态。
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
