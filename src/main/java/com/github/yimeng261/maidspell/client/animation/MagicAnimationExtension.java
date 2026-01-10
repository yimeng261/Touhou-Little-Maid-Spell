package com.github.yimeng261.maidspell.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.magic.MagicCastingAnimationManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 动画适配
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03 14:14
 */
@LittleMaidExtension
public class MagicAnimationExtension implements ILittleMaid {
    @Override
    @OnlyIn(Dist.CLIENT)
    public void registerMagicCastingAnimation(MagicCastingAnimationManager manager) {
        manager.register(new ISSCastingAnimationProvider());
    }
}
