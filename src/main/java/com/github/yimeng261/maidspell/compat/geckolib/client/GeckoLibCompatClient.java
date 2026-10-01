package com.github.yimeng261.maidspell.compat.geckolib.client;

import com.github.yimeng261.maidspell.compat.geckolib.client.renderer.entity.StarShadowSpearRenderer;
import com.github.yimeng261.maidspell.compat.geckolib.registry.GeckoLibCompatEntities;
import com.github.yimeng261.maidspell.compat.geckolib.registry.GeckoLibCompatItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * 需要在客户端注册、又碰到 GeckoLib 类型的那部分内容。由 {@code GeckoLibCompat} 按 GeckoLib 是否加载来调用。
 */
public final class GeckoLibCompatClient {

    private GeckoLibCompatClient() {
    }

    /**
     * 星影投枪蓄力时换用另一份物品模型，和原版三叉戟一样。
     *
     * <p>原版第三人称的投掷姿势和握持姿势差了整整一个方向（{@code star_shadow_spear_throwing.json}），
     * 少了这份 override，蓄力时枪头就是反的。判据名沿用原版的 {@code throwing}，
     * 属性是按物品注册的，不会和三叉戟冲突。
     */
    public static void onClientSetup() {
        ItemProperties.register(GeckoLibCompatItems.STAR_SHADOW_SPEAR.get(), new ResourceLocation("throwing"),
                (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }

    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GeckoLibCompatEntities.STAR_SHADOW_SPEAR.get(), StarShadowSpearRenderer::new);
    }
}
