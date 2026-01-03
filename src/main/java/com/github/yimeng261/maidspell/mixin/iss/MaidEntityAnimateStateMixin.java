package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 为女仆添加动画状态
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03 13:45
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class MaidEntityAnimateStateMixin implements CastingAnimateStateAccessor {
    @Unique
    private MagicCastingAnimateState maidspell$castingAnimateState = new MagicCastingAnimateState(IMagicCastingState.CastingPhase.NONE);

    @Override
    public MagicCastingAnimateState maidspell$getCastingAnimateState() {
        return maidspell$castingAnimateState;
    }
}
