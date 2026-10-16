package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** An additive emission pass keeps glow independent of the opaque model's visibility. */
final class SupernovaBlackHoleGlowLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private static final float GLOW_INTENSITY = 0.16F;
    static final float UNDERSIDE_GLOW_INTENSITY = 0.40F;

    SupernovaBlackHoleGlowLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T entity, BakedGeoModel model,
                       RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                       float partialTick, int light, int overlay) {
        RenderType glow = SupernovaBlackHoleRenderType.GLOW;
        getRenderer().reRender(model, poseStack, buffers, entity, glow, buffers.getBuffer(glow),
                partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                GLOW_INTENSITY, GLOW_INTENSITY, GLOW_INTENSITY, 1.0F);
    }
}
