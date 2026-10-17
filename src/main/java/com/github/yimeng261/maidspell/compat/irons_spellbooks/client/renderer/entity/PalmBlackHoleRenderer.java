package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation.PalmBlackHoleReleasePose;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleLocatorPose;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;

public class PalmBlackHoleRenderer extends SupernovaBlackHoleRenderer<PalmBlackHoleEntity> {
    private static final Map<PalmBlackHoleEntity, PalmBlackHoleReleasePose> LOCATOR_POSES = new WeakHashMap<>();
    private boolean locatorRender;
    public PalmBlackHoleRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override protected float modelScale(PalmBlackHoleEntity entity, float partialTick) {
        if (locatorRender) return PalmBlackHoleTimeline.LOCATOR_SCALE_MULTIPLIER;
        float age = entity.visualAge(partialTick);
        return age >= PalmBlackHoleTimeline.TOP_TICK && age < PalmBlackHoleTimeline.IMPACT_TICK
                ? PalmBlackHoleTimeline.LOCATOR_SCALE_MULTIPLIER : 0;
    }

    @Override protected void translateToCenter(PalmBlackHoleEntity entity, float partialTick, PoseStack poses) {
        if (locatorRender) return;
        PalmBlackHoleReleasePose release = LOCATOR_POSES.get(entity);
        if (release == null || !release.isNearRelease()) {
            // 释放之后才开始被追踪的实体，也能得到正确的设计朝向与大小。
            release = new PalmBlackHoleReleasePose(PalmBlackHoleTimeline.TOP_TICK,
                    new Vector3d(entity.getX(), entity.getY() + entity.visualHeight(PalmBlackHoleTimeline.TOP_TICK),
                            entity.getZ()), PalmBlackHoleLocatorPose.topPose(entity.releaseYaw()));
        }
        Vector3d center = release.centerAt(entity.visualAge(partialTick),
                new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
        // 渲染分发器已经平移到插值后的实体底部，
        // 这里减掉它，位置包就不会移动已定格的世界坐标中心。
        poses.translate(center.x - Mth.lerp(partialTick, entity.xOld, entity.getX()),
                center.y - Mth.lerp(partialTick, entity.yOld, entity.getY()),
                center.z - Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        poses.mulPose(release.rotation());
        Vector3f scale = release.scale();
        poses.scale(scale.x, scale.y, scale.z);
    }

    public void renderAtLocator(PalmBlackHoleEntity entity, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        locatorRender = true;
        try {
            super.render(entity, 0, partialTick, poses, buffers, light);
        } finally {
            locatorRender = false;
        }
    }

    static void rememberLocator(PalmBlackHoleEntity entity, Matrix4f local, LivingEntity witch,
                                float witchScale, float partialTick) {
        Matrix4f world = new Matrix4f().translate(0, 0.01F, 0).rotateY((180 - Mth.rotLerp(partialTick,
                witch.yBodyRotO, witch.yBodyRot)) * Mth.DEG_TO_RAD).scale(witchScale).mul(local);
        Vector3f offset = world.getTranslation(new Vector3f());
        Vector3d center = new Vector3d(Mth.lerp(partialTick, witch.xOld, witch.getX()) + offset.x,
                Mth.lerp(partialTick, witch.yOld, witch.getY()) + offset.y,
                Mth.lerp(partialTick, witch.zOld, witch.getZ()) + offset.z);
        LOCATOR_POSES.put(entity, new PalmBlackHoleReleasePose(entity.visualAge(partialTick), center, world));
    }

    @Override public void render(PalmBlackHoleEntity entity, float yaw, float partialTick,
                                  PoseStack poses, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, poses, buffers, light);
        float age = entity.visualAge(partialTick);
        if (PalmBlackHoleTimeline.isWarningActive(age)) {
            float pulse = 0.5F + 0.5F * Mth.sin((age - PalmBlackHoleTimeline.WARNING_START_TICK) * 0.35F);
            VertexConsumer glow = buffers.getBuffer(SupernovaBlackHoleRenderType.GLOW);
            // 整个倒计时期间，外边界保持在完整爆炸半径。
            drawWave(poses, glow, PalmBlackHoleTimeline.BLAST_RADIUS, 0.12F, 0.65F + pulse * 0.3F, 1);
            return;
        }
        if (age < PalmBlackHoleTimeline.IMPACT_TICK) return;
        float radius = PalmBlackHoleTimeline.waveRadius(age);
        float fade = Math.max(0, 1 - (age - PalmBlackHoleTimeline.IMPACT_TICK) / 28);
        VertexConsumer glow = buffers.getBuffer(SupernovaBlackHoleRenderType.GLOW);
        drawWave(poses, glow, radius, 0.15F, 0.7F, fade);
        drawWave(poses, glow, radius * 0.87F, 0.07F, 0.35F, fade);
    }

    private static void drawWave(PoseStack poses, VertexConsumer vertices, float radius,
                                  float y, float strength, float fade) {
        // 带贴图的环带随服务端造成伤害的波前半径推进。
        for (int i = 0; i < 96; i++) {
            double angle = i * Math.PI * 2 / 96, next = (i + 1) * Math.PI * 2 / 96;
            float inner = Math.max(0, radius - 0.6F);
            float[][] points = {{inner, (float) angle}, {radius, (float) angle},
                    {radius, (float) next}, {inner, (float) next}};
            for (int side = 0; side < 2; side++) {
                for (int k = 0; k < 4; k++) {
                    float[] point = points[side == 0 ? k : 3 - k];
                    vertices.addVertex(poses.last().pose(), point[0] * Mth.cos(point[1]), y,
                                    point[0] * Mth.sin(point[1]))
                            .setColor(strength * 0.8F * fade, strength * 0.45F * fade, strength * fade, 1)
                            .setUv(0.045F, 0.411F).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightTexture.FULL_BRIGHT).setNormal(0, side == 0 ? 1 : -1, 0);
                }
            }
        }
    }
}
