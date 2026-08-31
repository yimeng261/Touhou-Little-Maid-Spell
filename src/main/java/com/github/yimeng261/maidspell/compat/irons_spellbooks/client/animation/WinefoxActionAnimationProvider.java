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
 * 万法酒狐的动作动画走 TLM 自己的 {@code magic_casting} 通道。
 *
 * <p>迁移前这些动画挂在实体自带的 gecko4 {@code action} 控制器上；换成 TLM 的女仆渲染器之后
 * gecko4 那条路整条不再运行，得改用 TLM 的通道。{@code magic_casting} 排在固定表的第 14 位、
 * 在 {@code hold_mainhand}（第 11 位）之后，因此动作动画天然压得住持握姿势写的 {@code scale = 1}
 * —— 迁移前靠控制器注册顺序维持的那条约束，在这里是免费的。
 *
 * <p>它也是 TLM 全局唯一会调 {@code markNeedsReload()} 的地方，也就是唯一能让**同一条动画
 * 从第 0 帧重播**的入口。连段里第二次挥同一招全靠它。
 *
 * <h2>优先级 200，让位给施法</h2>
 * {@link #getPriority()} 报 200，高于 {@code ISSCastingAnimationProvider} 的默认 100。
 * TLM 的 {@code MagicCastingAnimationManager} 按优先级倒序排，注册顺序无关。
 *
 * <p>排在前面不等于抢通道：不是酒狐、或者酒狐没在放动作时，本 provider 报 NONE，
 * {@code predicateMagicCastingAnimation} 就 {@code continue} 到下一个 provider ——
 * 施法动画整条由 {@code ISSCastingAnimationProvider} 从铁魔法的 {@code SyncedSpellData}
 * 算出来，和普通女仆走同一条路。
 *
 * <p><b>报 NONE 时那次 {@code setLastCastingPhase(NONE)} 不会打乱后面的 ISS provider。</b>
 * TLM 把 {@code lastPhase} 在循环开始前读进局部变量、整轮不再重读，所以 ISS
 * 那边判「要不要 {@code markNeedsReload()}」看到的仍是上一 tick 的真实相位。
 * 唯一的交叉是：上一 tick 停在 INSTANT / END 且控制器还没停时，本 provider 会直接
 * {@code CONTINUE} 把这一 tick 让给正在播的一次性动作 —— 这正是 ISS provider
 * 自己在同样位置会做的事，只是提前了一步。
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
     * <ul>
     *   <li><b>战败</b>：一直报 CASTING。{@code death} 被加长到 10000 秒，靠「最后一帧之后
     *       没有下一帧」定格；持续占着通道才压得住 {@code main} 通道上的待机。
     *       报 CASTING 而不是 INSTANT，是因为 CASTING 每 tick 都重新 {@code setAnimation}
     *       却不会 reload，姿势稳稳地钉着；也顺带把施法 provider 挡在门外
     *       —— 她已经倒下了，不该再有施法动作。</li>
     *   <li><b>一次性动作</b>（近战 / 转阶段）：起始那一帧报 INSTANT，之后报 NONE，
     *       动画由 TLM 靠「上一帧 INSTANT 且控制器没停 → CONTINUE」放完。</li>
     *   <li><b>其余一律 NONE</b>，让给 {@code ISSCastingAnimationProvider}。</li>
     * </ul>
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
