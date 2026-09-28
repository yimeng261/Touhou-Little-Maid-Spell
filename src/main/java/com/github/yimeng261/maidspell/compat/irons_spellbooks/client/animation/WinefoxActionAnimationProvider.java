package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingAnimationProvider;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxCastingAnimateState;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTermination;
import org.jetbrains.annotations.Nullable;

/**
 * 酒狐动作动画占用 TLM 的 magic_casting 通道，优先于 ISS 施法动画。
 * 没有动作时返回 NONE，让后续 provider 处理施法；同一动作重播须重新加载控制器。
 */
public class WinefoxActionAnimationProvider implements IMagicCastingAnimationProvider {

    @Override
    public int getPriority() {
        return 200;
    }

    /**
     * 每只在屏女仆每帧都会走一次这里，所以第一句必须先把不是酒狐的挡掉。
     *
     * <p>顺便在这儿把相位算好写进状态 —— TLM 紧接着就读 {@code getCurrentPhase()}，
     * 分成两处算反而要多存一份。
     */
    @Override
    public @Nullable IMagicCastingState getMagicCastingState(IMaid maid) {
        if (!(maid instanceof MagicalWinefoxBossEntity boss)) {
            return null;
        }
        WinefoxCastingAnimateState state = boss.castingAnimateState();
        state.setCurrentPhase(currentPhase(boss, state));
        return state;
    }

    /**
     * 战败和循环动作持续报 CASTING；一次性动作首帧报 INSTANT，之后由 TLM 播完。
     * 其余状态交给 ISS 施法 provider。
     */
    private static IMagicCastingState.CastingPhase currentPhase(MagicalWinefoxBossEntity boss,
                                                                WinefoxCastingAnimateState state) {
        if (boss.isDefeated()) {
            return IMagicCastingState.CastingPhase.CASTING;
        }
        WinefoxAction action = boss.animationAction();
        if (action == WinefoxAction.NONE) {
            return IMagicCastingState.CastingPhase.NONE;
        }
        if (action.termination() == WinefoxTermination.LOOP) {
            return IMagicCastingState.CastingPhase.CASTING;
        }
        return state.claimSerial(boss.animationActionSerial())
                ? IMagicCastingState.CastingPhase.INSTANT
                : IMagicCastingState.CastingPhase.NONE;
    }

    /**
     * 注意：返回 null <b>不等于</b>让位。TLM 那边 builder 为 null 时只要控制器还没停就照样
     * {@code CONTINUE}，通道仍被占着。真要让位得让相位报 NONE。
     */
    @Override
    public @Nullable AnimationBuilder getAnimationBuilder(IMaid maid, IMagicCastingState state) {
        if (!(maid instanceof MagicalWinefoxBossEntity boss)) {
            return null;
        }
        if (boss.isDefeated()) {
            return build(WinefoxAction.DEFEAT.animationName(), WinefoxAction.DEFEAT.termination());
        }
        WinefoxAction action = boss.animationAction();
        // 兜底：相位不是 NONE 才会走到这儿，所以 action 也不会是 NONE。留着是因为
        // hasOwnAnimation() 才是「有没有轨道可播」的正主，将来加了别的无动画动作也不会漏。
        if (!action.hasOwnAnimation()) {
            return null;
        }
        return build(action.animationName(), action.termination());
    }

    private static AnimationBuilder build(String animationName, WinefoxTermination termination) {
        return new AnimationBuilder().addAnimation(animationName, loopType(termination));
    }

    private static ILoopType loopType(WinefoxTermination termination) {
        return switch (termination) {
            case LOOP -> ILoopType.EDefaultLoopTypes.LOOP;
            case HOLD_LAST_FRAME -> ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME;
            default -> ILoopType.EDefaultLoopTypes.PLAY_ONCE;
        };
    }
}
