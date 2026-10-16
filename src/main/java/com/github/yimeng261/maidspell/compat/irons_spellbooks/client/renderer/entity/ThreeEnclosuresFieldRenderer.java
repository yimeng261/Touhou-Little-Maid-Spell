package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ThreeEnclosuresFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 三棱柱的星位与光壁渲染。光壁使用无纹理的闪电渲染层，始终保持明亮。 */
public class ThreeEnclosuresFieldRenderer extends EntityRenderer<ThreeEnclosuresFieldEntity> {
    public ThreeEnclosuresFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(ThreeEnclosuresFieldEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        Vec3[] base = entity.getBaseVertices(partialTicks);
        Vec3[] top = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            top[i] = base[i].add(0.0D, ThreeEnclosuresFieldEntity.PRISM_HEIGHT, 0.0D);
        }

        float pulse = 0.5F + 0.5F * Mth.sin((entity.tickCount + partialTicks) * 0.18F);
        boolean expanding = entity.isExpanding();
        float red = expanding ? 0.78F : 0.35F;
        float green = expanding ? 0.42F + pulse * 0.20F : 0.78F + pulse * 0.12F;
        float blue = expanding ? 1.0F : 1.0F;

        VertexConsumer fill = bufferSource.getBuffer(RenderType.lightning());
        for (int i = 0; i < 3; i++) {
            int next = (i + 1) % 3;
            quad(fill, matrix, base[i], base[next], top[next], top[i], red, green, blue, 0.13F + pulse * 0.05F);
            // Render the reverse winding too: lightning quads are single-sided
            // under some client render paths, so the outer face must be visible.
            quad(fill, matrix, top[i], top[next], base[next], base[i], red, green, blue,
                    0.13F + pulse * 0.05F);
        }
        triangle(fill, matrix, base[0], base[1], base[2], red, green, blue, 0.07F + pulse * 0.03F);
        triangle(fill, matrix, top[2], top[1], top[0], red, green, blue, 0.05F + pulse * 0.03F);
        triangle(fill, matrix, base[2], base[1], base[0], red, green, blue, 0.07F + pulse * 0.03F);
        triangle(fill, matrix, top[0], top[1], top[2], red, green, blue, 0.05F + pulse * 0.03F);

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        float innerScale = 0.76F + pulse * 0.08F;
        for (int i = 0; i < 3; i++) {
            int next = (i + 1) % 3;
            line(lines, matrix, base[i], base[next], red, green, blue, 0.95F);
            line(lines, matrix, top[i], top[next], red, green, blue, 0.95F);
            line(lines, matrix, base[i], top[i], red, green, blue, 0.90F);
            line(lines, matrix, base[i], top[next], red, green, blue, 0.28F + pulse * 0.16F);
            line(lines, matrix, top[i], base[next], red, green, blue, 0.20F + pulse * 0.12F);
            drawTriangleLoop(lines, matrix, base, innerScale, 0.25F + pulse * 0.15F,
                    red, green, blue);
            drawTriangleLoop(lines, matrix, top, innerScale, 0.20F + pulse * 0.12F,
                    red, green, blue);
            drawStar(lines, matrix, base[i], 0.22F + pulse * 0.06F, red, green, blue);
            drawStar(lines, matrix, top[i], 0.22F + pulse * 0.06F, red, green, blue);
        }
        if (!expanding) {
            drawTargetLock(lines, matrix, entity, pulse, red, green, blue);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    private static void drawStar(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float size,
                                 float red, float green, float blue) {
        line(consumer, matrix, center.add(-size, 0.0D, 0.0D), center.add(size, 0.0D, 0.0D),
                red, green, blue, 1.0F);
        line(consumer, matrix, center.add(0.0D, 0.0D, -size), center.add(0.0D, 0.0D, size),
                red, green, blue, 1.0F);
        line(consumer, matrix, center.add(0.0D, -size, 0.0D), center.add(0.0D, size, 0.0D),
                red, green, blue, 1.0F);
    }

    private static void drawTriangleLoop(VertexConsumer consumer, Matrix4f matrix, Vec3[] vertices,
                                         float scale, float alpha, float red, float green, float blue) {
        Vec3[] inner = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            inner[i] = vertices[i].scale(scale);
        }
        for (int i = 0; i < 3; i++) {
            line(consumer, matrix, inner[i], inner[(i + 1) % 3], red, green, blue, alpha);
        }
    }

    /** 脉动的中心锁定标记，表示三垣连星锁定的是哪一个目标位置。 */
    private static void drawTargetLock(VertexConsumer consumer, Matrix4f matrix,
                                       ThreeEnclosuresFieldEntity entity, float pulse,
                                       float red, float green, float blue) {
        double radius = 0.58D + pulse * 0.12D;
        double angle = -entity.getAngle(0.0F) * 2.0D;
        Vec3 center = new Vec3(0.0D, 0.12D, 0.0D);
        for (int i = 0; i < 12; i++) {
            double a0 = angle + i * Math.PI * 2.0D / 12.0D;
            double a1 = angle + (i + 1) * Math.PI * 2.0D / 12.0D;
            // Keep small gaps between the twelve reticle segments.
            if (i % 3 == 2) {
                continue;
            }
            Vec3 from = center.add(Math.cos(a0) * radius, 0.0D, Math.sin(a0) * radius);
            Vec3 to = center.add(Math.cos(a1) * radius, 0.0D, Math.sin(a1) * radius);
            line(consumer, matrix, from, to, red, green, blue, 0.95F);
        }
        line(consumer, matrix, center.add(-0.42D, 0.0D, 0.0D), center.add(0.42D, 0.0D, 0.0D),
                1.0F, 0.92F, 0.72F, 0.95F);
        line(consumer, matrix, center.add(0.0D, 0.0D, -0.42D), center.add(0.0D, 0.0D, 0.42D),
                1.0F, 0.92F, 0.72F, 0.95F);
        line(consumer, matrix, center, center.add(0.0D, 1.1D + pulse * 0.35D, 0.0D),
                red, green, blue, 0.20F + pulse * 0.15F);
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to,
                             float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .color(red, green, blue, alpha)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .color(red, green, blue, alpha)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float red, float green, float blue, float alpha) {
        for (Vec3 point : new Vec3[] {a, b, c, d}) {
            consumer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                    .color(red, green, blue, alpha)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .endVertex();
        }
    }

    private static void triangle(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c,
                                 float red, float green, float blue, float alpha) {
        // The lightning render type uses QUADS; duplicate the final point to
        // form a degenerate quad that still fills the triangular face.
        for (Vec3 point : new Vec3[] {a, b, c, c}) {
            consumer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                    .color(red, green, blue, alpha)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .endVertex();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ThreeEnclosuresFieldEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
