package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 施法动画加载器
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-07-24 01:52
 */
public class CastingAnimationLoader {
    public static final ResourceLocation CASTING_MAID_ANIMATION = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "animation/iss.animation.json");

    public static void onDefaultGeckoAnimationEvent(DefaultGeckoAnimationEvent event) {
        // 游戏启动时事件系统是关闭状态，所以这里用了混入
        event.addAnimation(event.getMaidAnimationFile(), CASTING_MAID_ANIMATION);
    }
}
