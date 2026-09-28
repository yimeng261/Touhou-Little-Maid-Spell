package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;

import java.util.function.BiPredicate;

/**
 * 向 TLM 的全局 main 动画表注册酒狐状态。仅在客户端 setup 注册一次，
 * 谓词须排除其它女仆与坐姿酒狐，避免覆盖 TLM 的 sit 动画。
 */
public final class WinefoxMaidAnimationStates {
    /** 地面上算不算在走，与 TLM 的 {@code walk} 用同一个口径，两条谓词才不会重叠。 */
    private static final double MIN_LIMB_SWING = 0.05D;

    private WinefoxMaidAnimationStates() {
    }

    /** 只能调一次。调用点在客户端 setup 的 {@code enqueueWork} 里，见类注释。 */
    public static void register() {
        AnimationManager manager = AnimationManager.getInstance();
        manager.register(state("fly", boss((boss, event) -> !boss.isCurtsying()
                && isHovering(boss) && isMoving(event))));
        manager.register(state("phase_one_idle",
                boss((boss, event) -> !boss.isCurtsying() && !boss.isPhaseTwo()
                        && isIdlePose(boss, event))));
        manager.register(state("phase_two_idle",
                boss((boss, event) -> !boss.isCurtsying() && boss.isPhaseTwo()
                        && isIdlePose(boss, event))));
        manager.register(new AnimationState(
            "curtsy",
            ILoopType.EDefaultLoopTypes.PLAY_ONCE,
            1,
            boss((boss, event) -> boss.isCurtsying())
        ));
    }

    private static AnimationState state(String animationName,
                                        BiPredicate<IMaid, AnimationEvent<?>> predicate) {
        return new AnimationState(animationName, ILoopType.EDefaultLoopTypes.LOOP, 1, predicate);
    }

    /**
     * 把谓词收窄到酒狐本人，并且排除掉「坐在秋千上」——见类注释，那一段归 TLM 的
     * {@code sit}，两边同在优先级 1，不排除就变成谁先注册谁赢。
     */
    private static BiPredicate<IMaid, AnimationEvent<?>> boss(
            BiPredicate<MagicalWinefoxBossEntity, AnimationEvent<?>> predicate) {
        return (maid, event) -> maid instanceof MagicalWinefoxBossEntity boss
                && !boss.isSeated()
                && predicate.test(boss, event);
    }

    /**
     * 待机姿势：悬停不动，或者站在地上不动。
     *
     * <p>没重力地悬停时她永远 {@code !onGround()}，靠 TLM 的 {@code idle} 是等不到的 ——
     * {@code jump}(2) 会先命中。这也是这三条非补不可的原因。
     *
     * <p>无重力**下落**（还没锁定目标、被打飞）时返回 false，让位给 TLM 的 {@code jump}。
     */
    private static boolean isIdlePose(MagicalWinefoxBossEntity boss, AnimationEvent<?> event) {
        if (isHovering(boss)) {
            return !isMoving(event);
        }
        return boss.onGround() && !isMoving(event);
    }

    /** 锁定目标之后她就 {@code setNoGravity(true)} 常年浮空，这才是她的「飞」。 */
    private static boolean isHovering(MagicalWinefoxBossEntity boss) {
        return !boss.onGround() && boss.isNoGravity();
    }

    private static boolean isMoving(AnimationEvent<?> event) {
        return event.getLimbSwingAmount() > MIN_LIMB_SWING;
    }
}
