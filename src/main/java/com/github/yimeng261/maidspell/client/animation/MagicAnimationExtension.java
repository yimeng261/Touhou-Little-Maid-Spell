package com.github.yimeng261.maidspell.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.magic.MagicCastingAnimationManager;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation.WinefoxActionAnimationProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
        if (!IronsSpellbooksCompat.isLoaded()) {
            return;
        }
        manager.register(new ISSCastingAnimationProvider());
        // 万法酒狐的动作动画也走 magic_casting 通道，优先级 200 排在 ISS 那条（默认 100）前面。
        // 排在前面不等于抢通道：不是酒狐、或者酒狐没在放动作时它报 NONE 让位。
        manager.register(new WinefoxActionAnimationProvider());
    }
}
