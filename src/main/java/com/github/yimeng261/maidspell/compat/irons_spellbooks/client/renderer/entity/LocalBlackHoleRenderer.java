package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** 复刻铁魔法 3.16.3 的黑洞视觉效果，用于本模组自有的法术实体 */
public class LocalBlackHoleRenderer<T extends BlackHole> extends EntityRenderer<T> {
    private final ResourceLocation centerTexture;
    private final ResourceLocation beamTexture;
    private final int centerColor;
    private final int beamColor;
    private final int beamAlpha;
    private final int beamBuildTicks;
    private final int beamDensity;
    private final double hoverAboveEyes;

    protected LocalBlackHoleRenderer(EntityRendererProvider.Context context,
                                     ResourceLocation centerTexture, ResourceLocation beamTexture,
                                     int centerColor, int beamColor, int beamAlpha,
                                     int beamBuildTicks, int beamDensity,
                                     double hoverAboveEyes) {
        super(context);
        this.centerTexture = centerTexture;
        this.beamTexture = beamTexture;
        this.centerColor = centerColor;
        this.beamColor = beamColor;
        this.beamAlpha = beamAlpha;
        this.beamBuildTicks = beamBuildTicks;
        this.beamDensity = beamDensity;
        this.hoverAboveEyes = hoverAboveEyes;
    }

    @Override
    public void render(T entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        float radius = entity.getRadius();
        float entityScale = radius * 0.05F;
        float animationProgress = (entity.tickCount + partialTick) / beamBuildTicks;

        poseStack.pushPose();
        translateToVisualCenter(entity, partialTick, poseStack, radius);

        poseStack.pushPose();
        poseStack.scale(entityScale, entityScale, entityScale);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        drawCenter(poseStack.last(), buffers.getBuffer(RenderType.entityTranslucent(centerTexture)));
        poseStack.popPose();

        poseStack.pushPose();
        RandomSource random = RandomSource.create(432L);
        VertexConsumer beam = buffers.getBuffer(RenderType.energySwirl(beamTexture, 0.0F, 0.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 5.0F / radius));
        float progress = Math.min(animationProgress, 0.8F);
        for (int i = 0; i < (progress + progress * progress) * beamDensity; i++) {
            poseStack.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F
                    + animationProgress * 90.0F));
            float size = (random.nextFloat() * 10.0F + 7.5F) * entityScale * 0.4F;
            drawBeam(poseStack.last(), beam, size);
        }
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    private void translateToVisualCenter(T entity, float partialTick, PoseStack poseStack, float radius) {
        Entity owner = entity.getOwner();
        if (!(owner instanceof LivingEntity caster) || !caster.isAlive() || caster.level() != entity.level()) {
            poseStack.translate(0.0D, radius, 0.0D);
            return;
        }

        // 与 LevelRenderer 一致，按 xOld/yOld/zOld 插值实体位置，而非 Entity.getPosition(partialTick)
        Vec3 renderedEntityPos = interpolatedPosition(entity, partialTick);
        Vec3 renderedCasterPos = interpolatedPosition(caster, partialTick);
        poseStack.translate(renderedCasterPos.x - renderedEntityPos.x,
                renderedCasterPos.y + caster.getEyeHeight() + hoverAboveEyes - renderedEntityPos.y,
                renderedCasterPos.z - renderedEntityPos.z);
    }

    private static Vec3 interpolatedPosition(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp((double) partialTick, entity.xOld, entity.getX()),
                Mth.lerp((double) partialTick, entity.yOld, entity.getY()),
                Mth.lerp((double) partialTick, entity.zOld, entity.getZ()));
    }

    private void drawCenter(PoseStack.Pose pose, VertexConsumer consumer) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        vertex(consumer, matrix, normal, 0, -3, -3, 0, 1, centerColor, 255);
        vertex(consumer, matrix, normal, 0, 3, -3, 0, 0, centerColor, 255);
        vertex(consumer, matrix, normal, 0, 3, 3, 1, 0, centerColor, 255);
        vertex(consumer, matrix, normal, 0, -3, 3, 1, 1, centerColor, 255);
    }

    private void drawBeam(PoseStack.Pose pose, VertexConsumer consumer, float size) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        vertex(consumer, matrix, normal, 0, 0, 0, 0, 1, beamColor, beamAlpha);
        vertex(consumer, matrix, normal, 0, 3 * size, -size, 0, 0, 0, 0);
        vertex(consumer, matrix, normal, 0, 3 * size, size, 1, 0, 0, 0);
        vertex(consumer, matrix, normal, 0, 0, 0, 1, 1, beamColor, beamAlpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float z, float u, float v, int color, int alpha) {
        consumer.addVertex(matrix, x, y, z)
                .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.m10, normal.m11, normal.m12);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return centerTexture;
    }
}
