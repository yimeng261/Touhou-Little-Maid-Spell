package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.MagicalWinefoxBossEntity;

import java.util.function.BiPredicate;

/**
 * 把酒狐要的三条 {@code main} 通道动画补进 TLM 的全局动画状态表。
 *
 * <p>TLM 自带的那 20 条（{@code death} / {@code jump} / {@code walk} / {@code idle} …）里
 * 没有「飞」，也没有分阶段的待机 —— 这三条得自己补。
 *
 * <h2>那张表是全局的</h2>
 * {@code AnimationManager} 的表所有女仆共用，加进去对**每一只**女仆生效，
 * 所以每条谓词第一句都得先 {@code instanceof MagicalWinefoxBossEntity} 把别人挡掉。
 *
 * <p>而且 {@code CustomPackLoader.reloadPacks()} 清的东西里**没有它** ——
 * 必须在客户端 setup 里注册**一次**，绝不能挂进任何 reload 回调，
 * 否则每按一次 F3+T 就多追加一份。
 *
 * <h2>为什么都用优先级 1</h2>
 * {@code predicateMain} 从优先级 0 数到 4，**同一优先级内按注册先后**取第一个命中的谓词。
 * TLM 的 {@code AnimationRegister.registerAnimationState()} 什么时候跑不在我们手里，
 * 所以不能靠「排在 idle 前面」。优先级 1 压得住 {@code attacked}(2) / {@code jump}(2) /
 * {@code run}·{@code walk}(3) / {@code idle}(4)，又让得开 {@code death}·{@code sleep}·
 * {@code swim}·{@code ladder_*}(0)，与注册先后无关。
 *
 * <p>同在优先级 1 的 TLM 项（{@code sit} / {@code chair} / {@code boat} / 五种游戏）
 * 对酒狐全是 false：{@code IMaid.isMaidInSittingPose()} 默认 false，她也从不上载具。
 *
 * <p><b>代价</b>：TLM 的 {@code attacked}(2) 对酒狐变成不可达。这与迁移前一致 ——
 * 旧的 {@code mainAnimation} 里 {@code hurtTime > 0} 走的也是待机姿势，不是受击动画。
 */
public final class WinefoxMaidAnimationStates {
    /** 地面上算不算在走，与 TLM 的 {@code walk} 用同一个口径，两条谓词才不会重叠。 */
    private static final double MIN_LIMB_SWING = 0.05D;

    private WinefoxMaidAnimationStates() {
    }

    /** 只能调一次。调用点在客户端 setup 的 {@code enqueueWork} 里，见类注释。 */
    public static void register() {
        AnimationManager manager = AnimationManager.getInstance();
        manager.register(state("fly", boss((boss, event) -> isHovering(boss) && isMoving(event))));
        manager.register(state("phase_one_idle",
                boss((boss, event) -> !boss.isPhaseTwo() && isIdlePose(boss, event))));
        manager.register(state("phase_two_idle",
                boss((boss, event) -> boss.isPhaseTwo() && isIdlePose(boss, event))));
    }

    private static AnimationState state(String animationName,
                                        BiPredicate<IMaid, AnimationEvent<?>> predicate) {
        return new AnimationState(animationName, ILoopType.EDefaultLoopTypes.LOOP, 1, predicate);
    }

    private static BiPredicate<IMaid, AnimationEvent<?>> boss(
            BiPredicate<MagicalWinefoxBossEntity, AnimationEvent<?>> predicate) {
        return (maid, event) -> maid instanceof MagicalWinefoxBossEntity boss
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
