package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/**
 * 一条动画怎么结束。
 *
 * <p>中间三项与动画 JSON 里 {@code loop} 字段的三种写法一一对应，也就是 GeckoLib 侧
 * {@code thenPlay} / {@code thenLoop} / {@code thenPlayAndHold} 的选择依据。
 * 这层对应关系 原先是手抄的（Java 一份、JSON 一份），现在由 {@code WinefoxActionDataTest} 对账。
 *
 * <p>本枚举刻意不引任何 Minecraft / Forge / 铁魔法类型，对账测试才能不启动 Forge 直接读。
 */
public enum WinefoxTermination {

    /**
     * 没有自己的动画，无从谈终止。仅 {@link WinefoxAction#NONE} 用。
     */
    NONE,

    /**
     * 播一次就结束。JSON 里**没有** {@code loop} 字段。
     */
    ONE_SHOT,

    /**
     * 循环播放，直到外部显式停止。JSON 里 {@code "loop": true}。
     */
    LOOP,

    /**
     * 播完停在最后一帧。JSON 里 {@code "loop": "hold_on_last_frame"}。
     */
    HOLD_LAST_FRAME;

    /**
     * 这一项在动画 JSON 的 {@code loop} 字段上是否有对应写法。
     *
     * <p>只有 {@link #NONE} 没有：它是 Java 侧的占位，对账测试要跳过它。
     *
     * <p>原先还有一项 {@code EXTERNAL}（「终止时机由别处决定」），是给
     * {@code WinefoxAction.CAST} 用的。施法整条交给铁魔法之后那一项没了调用者，一并删掉。
     */
    public boolean hasJsonCounterpart() {
        return this != NONE;
    }
}
