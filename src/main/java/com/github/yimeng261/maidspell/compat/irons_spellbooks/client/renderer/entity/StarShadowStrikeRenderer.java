package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.StarShadowStrikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Random;

public class StarShadowStrikeRenderer extends EntityRenderer<StarShadowStrikeEntity> {
    private static final ResourceLocation[] TEXTURES = {
            texture(1), texture(2), texture(3), texture(4)
    };

    public StarShadowStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(StarShadowStrikeEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - entity.getYRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getXRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(new Random(31L * entity.getId()).nextInt(-8, 8)));

        drawSlash(poseStack.last(), entity, bufferSource,
                entity.getBbWidth() * 1.5F, entity.isMirrored());

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    private void drawSlash(PoseStack.Pose pose, StarShadowStrikeEntity entity,
                           MultiBufferSource bufferSource, float width, boolean mirrored) {
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityTranslucent(getTextureLocation(entity)));
        float halfWidth = width * 0.5F;
        float height = entity.getBbHeight() * 0.5F;

        vertex(consumer, poseMatrix, normalMatrix, -halfWidth, height, -halfWidth,
                0.0F, mirrored ? 0.0F : 1.0F);
        vertex(consumer, poseMatrix, normalMatrix, halfWidth, height, -halfWidth,
                1.0F, mirrored ? 0.0F : 1.0F);
        vertex(consumer, poseMatrix, normalMatrix, halfWidth, height, halfWidth,
                1.0F, mirrored ? 1.0F : 0.0F);
        vertex(consumer, poseMatrix, normalMatrix, -halfWidth, height, halfWidth,
                0.0F, mirrored ? 1.0F : 0.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                               float x, float y, float z, float u, float v) {
        consumer.vertex(poseMatrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(StarShadowStrikeEntity entity) {
        int frame = (entity.tickCount / StarShadowStrikeEntity.TICKS_PER_FRAME) % TEXTURES.length;
        return TEXTURES[frame];
    }

    private static ResourceLocation texture(int frame) {
        return new ResourceLocation(MaidSpellMod.MOD_ID,
                "textures/entity/star_shadow_strike/star_shadow_strike_" + frame + ".png");
    }
}
