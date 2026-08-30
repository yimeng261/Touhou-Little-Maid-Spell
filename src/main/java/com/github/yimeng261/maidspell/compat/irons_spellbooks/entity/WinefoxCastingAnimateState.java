package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;

/**
 * 万法酒狐挂在 TLM {@code magic_casting} 通道上的那一份状态。
 *
 * <p>每只酒狐一份，只在客户端读写：{@code WinefoxActionAnimationProvider} 每帧算出当前
 * {@link CastingPhase} 写进来，TLM 的 {@code predicateMagicCastingAnimation} 随后读走。
 *
 * <p>{@link #lastSerial} 是「这条一次性动作已经报过 INSTANT 了」的记号。
 * TLM 只在 {@code lastPhase} 不是 START / CASTING 时才会 {@code markNeedsReload()}，
 * 所以同一条动作必须**只在起始那一帧**报 INSTANT、之后报 NONE，
 * 靠 TLM「上一帧是 INSTANT 且控制器没停 → CONTINUE」那条分支把动画放完。
 *
 * <p>本类不引任何客户端专有类型：实体字段会跟着实体一起加载到专用服务器上，
 * 而 {@link IMagicCastingState} 在 TLM 的 {@code api} 包里，两端都安全。
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
     * TLM 用它做「这一帧跳过本 provider」的钩子，跳过后自己清零。我们不拿它当开关用，
     * 恒为 false。
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
