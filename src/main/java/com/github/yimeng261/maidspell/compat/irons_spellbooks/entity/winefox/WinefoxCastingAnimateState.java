package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;

/**
 * 每只酒狐的 magic_casting 动画状态，客户端读写。
 * lastSerial 防止一次性动作重复报告 INSTANT；本类不引用客户端专有类型。
 */
public class WinefoxCastingAnimateState implements IMagicCastingState {
    private CastingPhase phase = CastingPhase.NONE;
    private boolean cancelled;
    private int lastSerial = Integer.MIN_VALUE;

    @Override
    public CastingPhase getCurrentPhase() {
        return this.phase;
    }

    public void setCurrentPhase(CastingPhase phase) {
        this.phase = phase;
    }

    /**
     * 记下这个动作序号已经报过 INSTANT，返回它是不是本次新出现的。
     *
     * @return true 表示序号变了，本帧该报 INSTANT
     */
    public boolean claimSerial(int serial) {
        if (serial == this.lastSerial) {
            return false;
        }
        this.lastSerial = serial;
        return true;
    }

    /**
     * TLM 用它做「这一帧跳过本 provider」的钩子，跳过后自己清零。我们不拿它当开关用，恒为 false。
     */
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
