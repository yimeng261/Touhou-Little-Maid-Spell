package com.github.yimeng261.maidspell.client.spell;

import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;

/**
 * 动画状态访问器
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03 13:37
 */
public interface CastingAnimateStateAccessor {
    /**
     * 访问动画状态
     * <p>>
     * 注意只能在客户端访问
     *
     * @return 动画状态
     */
    MagicCastingAnimateState maidspell$getCastingAnimateState();
}
