package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.yimeng261.maidspell.event.CastingAnimationLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 使用混入加载动画
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-07-24 02:35
 */
@Mixin(value = GeckoModelLoader.class, remap = false)
public class GeckoModelLoaderMixin {
    @Inject(method = "loadDefaultAnimation", at = @At("TAIL"))
    private static void afterLoadDefaultAnimation(CallbackInfo ci) {
        CastingAnimationLoader.onDefaultGeckoAnimationEvent(new DefaultGeckoAnimationEvent(
            GeckoModelLoader.DEFAULT_MAID_ANIMATION_FILE,
            GeckoModelLoader.DEFAULT_TAC_ANIMATION_FILE,
            GeckoModelLoader.DEFAULT_CHAIR_ANIMATION_FILE));
    }
}
