package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 把 {@link WinefoxAction} / {@link WinefoxCastAnimation} 声明的终止方式翻成 GeckoLib 的
 * {@link RawAnimation}。
 *
 * <p>以前这 30 条 {@code RawAnimation} 是在实体里一条条手写的
 * （{@code RawAnimation.begin().thenPlay("staff_attack_1")} 这样），
 * 而终止方式在动画 JSON 的 {@code loop} 字段里又写了一遍——同一件事的第四份重复。
 * 现在只留一份声明，播放方式按规则推导。
 *
 * <p>单独成类而不是塞进实体，是为了让 {@code WinefoxAnimationBindingTest} 能直接验证这层翻译：
 * 本类只依赖 GeckoLib，不碰 Minecraft / Forge / 铁魔法，测试不启动 Forge 就能加载。
 */
public final class WinefoxAnimations {

    private static final Map<WinefoxAction, RawAnimation> ACTIONS = buildActions();
    private static final Map<WinefoxCastAnimation, RawAnimation> CASTS = buildCasts();

    private WinefoxAnimations() {
    }

    /** 动作动画；{@link WinefoxAction#NONE} 与 {@link WinefoxAction#CAST} 没有，返回 {@code null}。 */
    public static RawAnimation of(WinefoxAction action) {
        return ACTIONS.get(action);
    }

    public static RawAnimation of(WinefoxCastAnimation cast) {
        return CASTS.get(cast);
    }

    /**
     * 终止方式 → 播放方式的全部规则，就这三条。
     *
     * <p>{@code NONE} / {@code EXTERNAL} 没有自己的动画，走到这里说明枚举声明写错了，直接炸，
     * 免得在运行时变成一条静默不播的轨道。
     */
    public static RawAnimation build(String animationName, WinefoxTermination termination) {
        return switch (termination) {
            case ONE_SHOT -> RawAnimation.begin().thenPlay(animationName);
            case LOOP -> RawAnimation.begin().thenLoop(animationName);
            case HOLD_LAST_FRAME -> RawAnimation.begin().thenPlayAndHold(animationName);
            case NONE, EXTERNAL -> throw new IllegalStateException(
                    animationName + " has no playable termination: " + termination);
        };
    }

    private static Map<WinefoxAction, RawAnimation> buildActions() {
        Map<WinefoxAction, RawAnimation> animations = new EnumMap<>(WinefoxAction.class);
        for (WinefoxAction action : WinefoxAction.values()) {
            if (action.hasOwnAnimation()) {
                animations.put(action, build(action.animationName(), action.termination()));
            }
        }
        return Collections.unmodifiableMap(animations);
    }

    private static Map<WinefoxCastAnimation, RawAnimation> buildCasts() {
        Map<WinefoxCastAnimation, RawAnimation> animations = new EnumMap<>(WinefoxCastAnimation.class);
        for (WinefoxCastAnimation cast : WinefoxCastAnimation.values()) {
            animations.put(cast, build(cast.animationName(), cast.termination()));
        }
        return Collections.unmodifiableMap(animations);
    }
}
