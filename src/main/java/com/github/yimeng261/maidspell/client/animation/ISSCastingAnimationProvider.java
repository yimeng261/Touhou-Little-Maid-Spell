package com.github.yimeng261.maidspell.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingAnimationProvider;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.Optional;

/**
 * 铁魔法施法动画适配
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03 10:09
 */
public class ISSCastingAnimationProvider implements IMagicCastingAnimationProvider {
    private static final String ANIMATION_NAME_PREFIX = "iss:";

    @Override
    public @Nullable IMagicCastingState getMagicCastingState(IMaid maid) {
        if (!(maid.asEntity() instanceof CastingAnimateStateAccessor animateStateAccessor)) {
            return null;
        }
        return animateStateAccessor.maidspell$getCastingAnimateState();
    }

    @Override
    public @Nullable AnimationBuilder getAnimationBuilder(IMaid maid, IMagicCastingState state) {
        if (!(state instanceof MagicCastingAnimateState animateState)) {
            return null;
        }
        if (animateState.getCurrentPhase() == IMagicCastingState.CastingPhase.START
                || animateState.getCurrentPhase() == IMagicCastingState.CastingPhase.CASTING
                || animateState.getCurrentPhase() == IMagicCastingState.CastingPhase.INSTANT) {
            return getStartAnimationFromSpell(animateState);
        } else if (animateState.getCurrentPhase() == IMagicCastingState.CastingPhase.END) {
            return getFinishAnimationFromSpell(animateState);
        }
        return null;
    }

    private AnimationBuilder getStartAnimationFromSpell(MagicCastingAnimateState animateState) {
        SpellData castingSpell = animateState.getCastingSpell();
        AbstractSpell spell;
        if (castingSpell == null || castingSpell.getSpell() == SpellRegistry.none()) {
            spell = animateState.getInstantCastSpellType();
        } else {
            spell = castingSpell.getSpell();
        }
        if (spell == null || spell == SpellRegistry.none()) {
            return null;
        }
        Optional<RawAnimation> opRawAnimation = spell.getCastStartAnimation().getForMob();
        if (opRawAnimation.isPresent()) {
            RawAnimation rawAnimation = opRawAnimation.get();
            AnimationBuilder builder = new AnimationBuilder();
            for (RawAnimation.Stage animationStage : rawAnimation.getAnimationStages()) {
                if (animationStage.loopType() == Animation.LoopType.LOOP) {
                    builder.loop(ANIMATION_NAME_PREFIX + animationStage.animationName());
                } else if (animationStage.loopType() == Animation.LoopType.PLAY_ONCE) {
                    builder.playOnce(ANIMATION_NAME_PREFIX + animationStage.animationName());
                } else if (animationStage.loopType() == Animation.LoopType.HOLD_ON_LAST_FRAME) {
                    builder.playAndHold(ANIMATION_NAME_PREFIX + animationStage.animationName());
                }
            }
            animateState.setCancelled(false);
            if (spell.getCastType() == CastType.INSTANT) {
                animateState.clearInstantCastSpellType();
            }
            return builder;
        } else {
            animateState.setCancelled(true);
            return null;
        }
    }

    private AnimationBuilder getFinishAnimationFromSpell(MagicCastingAnimateState animateState) {
        AbstractSpell spell = animateState.getCastingSpell().getSpell();
        Optional<RawAnimation> opRawAnimation = spell.getCastFinishAnimation().getForMob();
        if (spell.getCastFinishAnimation().isPass) {
            animateState.setCancelled(false);
            return null;
        }
        if (opRawAnimation.isPresent()) {
            RawAnimation rawAnimation = opRawAnimation.get();
            AnimationBuilder builder = new AnimationBuilder();
            for (RawAnimation.Stage animationStage : rawAnimation.getAnimationStages()) {
                if (animationStage.loopType() == Animation.LoopType.LOOP) {
                    builder.loop(ANIMATION_NAME_PREFIX + animationStage.animationName());
                } else if (animationStage.loopType() == Animation.LoopType.PLAY_ONCE) {
                    builder.playOnce(ANIMATION_NAME_PREFIX + animationStage.animationName());
                } else if (animationStage.loopType() == Animation.LoopType.HOLD_ON_LAST_FRAME) {
                    builder.playAndHold(ANIMATION_NAME_PREFIX + animationStage.animationName());
                }
            }
            animateState.setCancelled(false);
            return builder;
        } else {
            animateState.setCancelled(true);
            return null;
        }
    }
}
