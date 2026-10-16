package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SevenStarGuidanceEntity;
import io.redspace.ironsspellbooks.render.RenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Pixel-painted seals surround the seven textured, three-dimensional crystals. */
public class SevenStarGuidanceRenderer extends EntityRenderer<SevenStarGuidanceEntity> {
    private static final ResourceLocation OUTER_SEAL = texture("outer_seal");
    private static final ResourceLocation INNER_SEAL = texture("inner_seal");
    private static final ResourceLocation CONSTELLATION = texture("constellation");
    private static final ResourceLocation CRYSTAL_FACE = texture("crystal_face");
    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation("irons_spellbooks", "textures/entity/ray/beacon_beam.png");
    private static final RenderType BEAM_RENDER_TYPE = createBeamRenderType();
    private static final float[][] STRIKE_COLORS = {
            {0.42F, 0.24F, 1.00F}, {0.62F, 0.18F, 1.00F}, {0.92F, 0.18F, 1.00F},
            {1.00F, 0.18F, 0.72F}, {1.00F, 0.44F, 0.82F}, {0.78F, 0.58F, 1.00F},
            {1.00F, 0.30F, 0.92F}
    };
    // Matches the pixel-to-world coordinates in tools/draw_seven_star_textures.py.
    private static final float SEAL_HALF_EXTENT = 10.4F;
    private static final int[][] OCTA_FACES = {
            {0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2},
            {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}
    };
    private static final Vec3[] FINAL_STAR_OFFSETS = {
            new Vec3(-2.0D, 0.0D, 1.5D), new Vec3(-0.5D, 0.0D, 2.0D),
            new Vec3(1.0D, 0.0D, 1.5D), new Vec3(1.5D, 0.0D, 0.0D),
            new Vec3(2.5D, 0.0D, -1.0D), new Vec3(3.25D, 0.0D, -1.5D),
            new Vec3(4.0D, 0.0D, -1.5D)
    };

    public SevenStarGuidanceRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SevenStarGuidanceEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        float age = entity.tickCount + partialTicks;
        float constellationRotation = getConstellationRotation(entity, age);
        int finaleStage = entity.getFinaleStage();
        int litThrough = (int) Math.floor((age - SevenStarGuidanceEntity.FIRST_STRIKE_TICK)
                / SevenStarGuidanceEntity.STRIKE_INTERVAL);
        litThrough = Math.max(-1, Math.min(6, litThrough));

        Vec3 arrayCenter = new Vec3(0.0D, SevenStarGuidanceEntity.SKY_HEIGHT, 0.0D);
        float formation = Math.min(1.0F, age / SevenStarGuidanceEntity.FIRST_STRIKE_TICK);
        if (finaleStage == 0) {
            drawSeal(bufferSource, matrix, OUTER_SEAL, arrayCenter.add(0.0D, -0.10D, 0.0D),
                    age * 0.012F, formation * 0.92F);
            drawSeal(bufferSource, matrix, INNER_SEAL, arrayCenter.add(0.0D, -0.05D, 0.0D),
                    -age * 0.018F, formation * 0.72F);
            drawSeal(bufferSource, matrix, CONSTELLATION, arrayCenter,
                    constellationRotation, formation * 0.95F);
        }

        VertexConsumer crystals = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL_FACE));

        for (int i = 0; i < 7; i++) {
            Vec3 point = entity.getRenderStarPoint(i, constellationRotation);
            boolean lit = i <= litThrough;
            float pulse = 0.88F + 0.12F * (float) Math.sin(age * 0.18F + i * 0.9F);
            drawOctahedron(crystals, matrix, point, (lit ? 0.48F : 0.32F) * pulse,
                    lit ? 1.0F : 0.60F, lit ? 1.0F : 0.50F, lit ? 1.0F : 0.80F,
                    formation * (lit ? 0.98F : 0.72F),
                    age * 0.055F + i * 0.35F);
        }

        // 目标从法阵成形阶段就显示锁定框；最后 12 tick 会再叠加斗柄旋转和强光束。
        Entity lockedTarget = entity.level().getEntity(entity.getTargetEntityId());
        if (lockedTarget != null) {
            Vec3 lockedPoint = lockedTarget.position().subtract(entity.position())
                    .add(0.0D, lockedTarget.getBbHeight() * 0.5D, 0.0D);
            float lockPulse = 0.78F + 0.22F * (float) Math.sin(age * 0.22F);
            float lockAlpha = Math.min(0.78F, 0.34F + age / 100.0F) * lockPulse;
            drawLockBrackets(bufferSource, matrix, lockedPoint, 0.72F, age * 0.045F, lockAlpha);
            drawRing(bufferSource, matrix, lockedPoint, 1.25F + 0.10F * (float) Math.sin(age * 0.16F),
                    -age * 0.045F, 0.80F, 0.20F, 1.0F, lockAlpha * 0.75F, 24);
        }

        if (finaleStage > 0 && lockedTarget != null) {
            drawFinalConstellation(entity, lockedTarget, finaleStage, age, matrix, bufferSource);
        }

        if (age >= SevenStarGuidanceEntity.FIRST_STRIKE_TICK
                && age < SevenStarGuidanceEntity.FIRST_STRIKE_TICK
                + SevenStarGuidanceEntity.STAR_COUNT * SevenStarGuidanceEntity.STRIKE_INTERVAL) {
            int strike = Math.min(6, Math.max(0,
                    (int) ((age - SevenStarGuidanceEntity.FIRST_STRIKE_TICK)
                            / SevenStarGuidanceEntity.STRIKE_INTERVAL)));
            Entity target = entity.level().getEntity(entity.getTargetEntityId());
            if (target != null) {
                Vec3 targetPoint = target.position().subtract(entity.position())
                        .add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
                Vec3 star = entity.getRenderStarPoint(strike, constellationRotation);
                float elapsed = (age - SevenStarGuidanceEntity.FIRST_STRIKE_TICK)
                        % SevenStarGuidanceEntity.STRIKE_INTERVAL;
                float pulse = 1.0F - elapsed / SevenStarGuidanceEntity.STRIKE_INTERVAL;
                float[] color = STRIKE_COLORS[strike];
                drawSunbeam(bufferSource, matrix, star, targetPoint, age,
                        0.16F + 0.44F * pulse * pulse, 0.28F + 0.72F * pulse,
                        color[0], color[1], color[2]);
                drawRing(bufferSource, matrix, targetPoint, 1.8F + 0.12F * (age % 10.0F),
                        age * 0.06F, 0.88F, 0.20F, 1.0F, 0.80F, 20);
            } else {
                Vec3 star = entity.getRenderStarPoint(strike, constellationRotation);
                float[] color = STRIKE_COLORS[strike];
                drawSunbeam(bufferSource, matrix, star,
                        star.add(0.0D, -SevenStarGuidanceEntity.SKY_HEIGHT, 0.0D), age, 0.22F, 0.50F,
                        color[0], color[1], color[2]);
            }
        }

        if (age >= SevenStarGuidanceEntity.FIRST_STRIKE_TICK
                + 7 * SevenStarGuidanceEntity.STRIKE_INTERVAL) {
            Entity target = entity.level().getEntity(entity.getTargetEntityId());
            if (target != null) {
                Vec3 targetPoint = target.position().subtract(entity.position()).add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
                float aimAlpha = Math.min(1.0F, (age - (SevenStarGuidanceEntity.FINAL_TICK
                        - SevenStarGuidanceEntity.AIM_TICKS)) / 5.0F);
                Vec3 handle = entity.getRenderStarPoint(6, constellationRotation);
                if (age < SevenStarGuidanceEntity.FINAL_TICK) {
                    line(bufferSource.getBuffer(RenderType.lines()), matrix, handle, targetPoint,
                            0.96F, 0.30F, 1.0F, aimAlpha);
                } else {
                    float fade = Math.max(0.0F, 1.0F - (age - SevenStarGuidanceEntity.FINAL_TICK)
                            / SevenStarGuidanceEntity.FINAL_FADE_TICKS);
                    drawSunbeam(bufferSource, matrix, handle, targetPoint, age,
                            0.12F + 0.95F * fade * fade, fade, 1.0F, 0.22F, 0.95F);
                }
                drawReticle(bufferSource, matrix, targetPoint, 0.65F, aimAlpha);
                drawRing(bufferSource, matrix, targetPoint, 2.0F, -age * 0.05F,
                        0.80F, 0.16F, 1.0F, aimAlpha * 0.85F, 24);
            }
        }

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    private static void drawOctahedron(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float size,
                                       float red, float green, float blue, float alpha, float rotation) {
        Vec3[] vertices = octahedron(center, size, rotation);
        for (int i = 0; i < OCTA_FACES.length; i++) {
            int[] face = OCTA_FACES[i];
            Vec3 a = vertices[face[0]], b = vertices[face[1]], c = vertices[face[2]];
            Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
            float shade = 0.74F + (i % 4) * 0.08F;
            // A repeated fourth vertex submits a triangle in a QUADS buffer.
            texturedVertex(consumer, matrix, a, 0.5F, 0.0F, normal, red * shade, green * shade, blue * shade, alpha);
            texturedVertex(consumer, matrix, b, 0.0F, 1.0F, normal, red * shade, green * shade, blue * shade, alpha);
            texturedVertex(consumer, matrix, c, 1.0F, 1.0F, normal, red * shade, green * shade, blue * shade, alpha);
            texturedVertex(consumer, matrix, c, 1.0F, 1.0F, normal, red * shade, green * shade, blue * shade, alpha);
        }
    }

    private static void drawFinalConstellation(SevenStarGuidanceEntity entity, Entity target,
                                               int stage, float age, Matrix4f matrix,
                                               MultiBufferSource buffers) {
        Vec3 forward = target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 1.0E-5D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(up).normalize();
        Vec3 center = target.position().subtract(entity.position())
                .add(0.0D, target.getBbHeight() * 0.52D, 0.0D)
                .add(forward.scale(Math.max(0.45D, target.getBbWidth() * 0.65D)));
        double scale = Mth.clamp(Math.max(target.getBbWidth() * 1.8D / 1.5D,
                target.getBbHeight() * 0.72D / 2.0D), 0.38D, 1.8D);
        int mask = entity.getFinaleMask();
        drawFinalConstellation(center, right, up, forward, scale, mask, stage, age, matrix, buffers);
    }

    static void drawFinalConstellation(Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
                                       double scale, int mask, int stage, float age,
                                       Matrix4f matrix, MultiBufferSource buffers) {
        int litCount = stage == 1
                ? Mth.clamp((int) ((age - SevenStarGuidanceEntity.FINAL_TICK)
                / (SevenStarGuidanceEntity.FINALE_DISPLAY_TICKS / 7.0F)) + 1, 0, 7)
                : 7;

        Vec3[] points = new Vec3[7];
        for (int i = 0; i < points.length; i++) {
            Vec3 offset = new Vec3(FINAL_STAR_OFFSETS[i].x / 4.0D,
                    -FINAL_STAR_OFFSETS[i].z / 2.0D, 0.0D);
            points[i] = center.add(right.scale(offset.x * scale)).add(up.scale(offset.y * scale));
        }

        for (int i = 0; i < points.length - 1; i++) {
            if ((mask & (1 << i)) != 0 && (mask & (1 << (i + 1))) != 0) {
                if (stage == 1) {
                    float alpha = i < litCount - 1 ? 0.95F : 0.25F;
                    drawSunbeam(buffers, matrix, points[i], points[i + 1], age,
                            Math.max(0.035F, (float) scale * 0.045F), alpha,
                            0.82F, 0.20F, 1.0F);
                } else {
                    drawShatteredEdge(buffers, matrix, points[i], points[i + 1], right, up, age,
                            Math.min(1.0F, (age - SevenStarGuidanceEntity.FINALE_SHATTER_TICK) / 8.0F));
                }
            }
        }

        // Edge effects switch the shared BufferBuilder's vertex format.
        // Acquire the crystal consumer only after those effects are complete.
        VertexConsumer crystals = buffers.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL_FACE));
        for (int i = 0; i < points.length; i++) {
            boolean hit = (mask & (1 << i)) != 0;
            boolean lit = hit && (stage == 2 || i < litCount);
            float size = (float) scale * (lit ? 0.14F : 0.09F);
            drawOrientedOctahedron(crystals, matrix, points[i], size, right, up, forward,
                    lit ? 1.0F : 0.30F, lit ? 0.80F : 0.22F, 1.0F, lit ? 1.0F : 0.42F,
                    age * 0.08F + i * 0.3F);
        }
    }

    private static void drawShatteredEdge(MultiBufferSource buffers, Matrix4f matrix, Vec3 from, Vec3 to,
                                          Vec3 right, Vec3 up, float age, float progress) {
        Vec3 direction = to.subtract(from);
        Vec3 midpoint = from.add(direction.scale(0.5D));
        Vec3 drift = right.scale(Math.sin((from.x + to.z) * 3.0D) * progress * 0.65D)
                .add(up.scale(progress * 0.4D));
        float alpha = 1.0F - progress * 0.35F;
        Vec3 firstEnd = midpoint.add(drift).add(direction.scale(-0.10D));
        Vec3 secondStart = midpoint.add(drift).add(direction.scale(0.10D));
        drawSunbeam(buffers, matrix, from, firstEnd, age, 0.075F, alpha,
                0.92F, 0.22F, 1.0F);
        drawSunbeam(buffers, matrix, secondStart, to, age + 0.7F, 0.075F, alpha,
                0.70F, 0.16F, 1.0F);
        VertexConsumer shards = buffers.getBuffer(RenderType.lines());
        line(shards, matrix, midpoint.add(drift), midpoint.add(drift).add(right.scale(0.5D)),
                1.0F, 0.62F, 0.95F, 1.0F - progress * 0.25F);
    }

    private static void drawOrientedOctahedron(VertexConsumer consumer, Matrix4f matrix, Vec3 center,
                                               float size, Vec3 right, Vec3 up, Vec3 forward,
                                               float red, float green, float blue, float alpha, float rotation) {
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        Vec3 r = right.scale(cos).add(up.scale(sin));
        Vec3 u = up.scale(cos).subtract(right.scale(sin));
        Vec3[] vertices = {
                center.add(u.scale(size)), center.subtract(u.scale(size)),
                center.add(r.scale(size)), center.subtract(r.scale(size)),
                center.add(forward.scale(size)), center.subtract(forward.scale(size))
        };
        int[][] faces = {
                {0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2},
                {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}
        };
        for (int[] face : faces) {
            Vec3 normal = vertices[face[1]].subtract(vertices[face[0]])
                    .cross(vertices[face[2]].subtract(vertices[face[0]])).normalize();
            texturedVertex(consumer, matrix, vertices[face[0]], 0.5F, 0.0F, normal, red, green, blue, alpha);
            texturedVertex(consumer, matrix, vertices[face[1]], 0.0F, 1.0F, normal, red, green, blue, alpha);
            texturedVertex(consumer, matrix, vertices[face[2]], 1.0F, 1.0F, normal, red, green, blue, alpha);
            texturedVertex(consumer, matrix, vertices[face[2]], 1.0F, 1.0F, normal, red, green, blue, alpha);
        }
    }

    private static void drawSeal(MultiBufferSource buffers, Matrix4f matrix, ResourceLocation texture,
                                 Vec3 center, float rotation, float alpha) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        float[][] corners = {{-1, -1, 0, 0}, {1, -1, 1, 0}, {1, 1, 1, 1}, {-1, 1, 0, 1}};
        for (float[] corner : corners) {
            double x = corner[0] * SEAL_HALF_EXTENT, z = corner[1] * SEAL_HALF_EXTENT;
            Vec3 point = center.add(x * cos - z * sin, 0.0D, x * sin + z * cos);
            texturedVertex(consumer, matrix, point, corner[2], corner[3], new Vec3(0, -1, 0),
                    1.0F, 1.0F, 1.0F, alpha);
        }
    }

    private static void texturedVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 point,
                                       float u, float v, Vec3 normal, float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal((float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/seven_star_guidance/" + name + ".png");
    }

    private static Vec3[] octahedron(Vec3 center, float size, float rotation) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        Vec3 east = center.add(size * cos, 0.0D, size * sin);
        Vec3 west = center.add(-size * cos, 0.0D, -size * sin);
        Vec3 north = center.add(-size * sin, 0.0D, size * cos);
        Vec3 south = center.add(size * sin, 0.0D, -size * cos);
        return new Vec3[] {
                center.add(0.0D, size, 0.0D),
                center.add(0.0D, -size, 0.0D),
                east, west, north, south
        };
    }

    private static float getConstellationRotation(SevenStarGuidanceEntity entity, float age) {
        float aimStart = SevenStarGuidanceEntity.FIRST_STRIKE_TICK
                + SevenStarGuidanceEntity.STAR_COUNT * SevenStarGuidanceEntity.STRIKE_INTERVAL;
        if (age < aimStart) {
            return 0.0F;
        }
        Entity target = entity.level().getEntity(entity.getTargetEntityId());
        if (target == null) {
            return 0.0F;
        }
        Vec3 targetOffset = target.position().subtract(entity.position());
        double targetAngle = Math.atan2(targetOffset.z, targetOffset.x);
        Vec3 handle = entity.getRenderStarPoint(6);
        double handleAngle = Math.atan2(handle.z, handle.x);
        double delta = targetAngle - handleAngle;
        while (delta > Math.PI) delta -= Math.PI * 2.0D;
        while (delta < -Math.PI) delta += Math.PI * 2.0D;
        float progress = Math.min(1.0F,
                Math.max(0.0F, (age - aimStart) / SevenStarGuidanceEntity.AIM_TICKS));
        return (float) (delta * progress);
    }

    static void drawReticle(MultiBufferSource buffers, Matrix4f matrix, Vec3 center,
                                    float radius, float alpha) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        line(consumer, matrix, center.add(-radius, 0, -radius), center.add(radius, 0, -radius),
                0.88F, 0.24F, 1.0F, alpha);
        line(consumer, matrix, center.add(radius, 0, -radius), center.add(radius, 0, radius),
                0.88F, 0.24F, 1.0F, alpha);
        line(consumer, matrix, center.add(radius, 0, radius), center.add(-radius, 0, radius),
                0.88F, 0.24F, 1.0F, alpha);
        line(consumer, matrix, center.add(-radius, 0, radius), center.add(-radius, 0, -radius),
                0.88F, 0.24F, 1.0F, alpha);
    }

    static void drawLockBrackets(MultiBufferSource buffers, Matrix4f matrix, Vec3 center,
                                         float radius, float rotation, float alpha) {
        // Consumers must not be retained across calls that request another render type.
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        Vec3[] points = new Vec3[8];
        for (int i = 0; i < points.length; i++) {
            double angle = rotation + Math.PI * 2.0D * i / points.length;
            points[i] = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
        }
        for (int i = 0; i < points.length; i += 2) {
            line(consumer, matrix, points[i], points[(i + 1) % points.length],
                    0.95F, 0.28F, 1.0F, alpha);
        }
    }

    private static RenderType createBeamRenderType() {
        try {
            return RenderHelper.CustomerRenderType.magic(BEAM_TEXTURE);
        } catch (NoSuchMethodError ignored) {
            // Older ISS SunbeamRenderer uses the vanilla additive eyes layer.
            return RenderType.eyes(BEAM_TEXTURE);
        }
    }

    private static void drawSunbeam(MultiBufferSource buffers, Matrix4f matrix, Vec3 from, Vec3 to,
                                    float age, float radius, float intensity,
                                    float red, float green, float blue) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1.0E-6D || intensity <= 0.0F) {
            return;
        }
        Vec3 direction = delta.normalize();
        Vec3 reference = Math.abs(direction.y) > 0.95D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = direction.cross(reference).normalize();
        Vec3 up = right.cross(direction).normalize();
        double angle = age * 0.055D;
        Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
        Vec3 tangent = up.scale(Math.cos(angle)).subtract(right.scale(Math.sin(angle)));
        VertexConsumer consumer = buffers.getBuffer(BEAM_RENDER_TYPE);
        float uvOffset = -(age * 0.16F % 1.0F);
        float repeats = (float) (delta.length() / 3.0D);

        // Like Sunbeam: textured additive hulls with a narrow bright core and coloured outer glow.
        drawBeamHull(consumer, matrix, from, to, radial, tangent, radius * 1.45F,
                uvOffset, repeats, red * 0.55F, green * 0.55F, blue * 0.85F, intensity * 0.24F);
        drawBeamHull(consumer, matrix, from, to, radial, tangent, radius,
                uvOffset, repeats, red * 0.90F, green * 0.90F, blue, intensity * 0.58F);
        drawBeamHull(consumer, matrix, from, to, radial, tangent, radius * 0.42F,
                uvOffset, repeats, Math.min(1.0F, red + 0.12F), Math.min(1.0F, green + 0.12F),
                Math.min(1.0F, blue + 0.08F), intensity);
    }

    private static void drawBeamHull(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to,
                                     Vec3 radial, Vec3 tangent, float radius, float uvOffset, float repeats,
                                     float red, float green, float blue, float intensity) {
        Vec3[] corners = {
                radial.add(tangent).scale(radius), radial.subtract(tangent).scale(radius),
                radial.add(tangent).scale(-radius), tangent.subtract(radial).scale(radius)
        };
        for (int i = 0; i < corners.length; i++) {
            Vec3 a = corners[i], b = corners[(i + 1) % corners.length];
            Vec3 normal = a.add(b).normalize();
            // Clockwise cross-section plus this winding produces outward-facing hull sides.
            texturedVertex(consumer, matrix, from.add(a), 0, uvOffset, normal,
                    red * intensity, green * intensity, blue * intensity, intensity);
            texturedVertex(consumer, matrix, from.add(b), 1, uvOffset, normal,
                    red * intensity, green * intensity, blue * intensity, intensity);
            texturedVertex(consumer, matrix, to.add(b), 1, uvOffset + repeats, normal,
                    red * intensity, green * intensity, blue * intensity, intensity);
            texturedVertex(consumer, matrix, to.add(a), 0, uvOffset + repeats, normal,
                    red * intensity, green * intensity, blue * intensity, intensity);
        }
    }

    static void drawRing(MultiBufferSource buffers, Matrix4f matrix, Vec3 center, float radius,
                                 float rotation, float red, float green, float blue, float alpha, int segments) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        Vec3 previous = null;
        for (int i = 0; i <= segments; i++) {
            double angle = rotation + Math.PI * 2.0D * i / segments;
            Vec3 point = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            if (previous != null) {
                line(consumer, matrix, previous, point, red, green, blue, alpha);
            }
            previous = point;
        }
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to,
                             float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .color(red, green, blue, alpha).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .color(red, green, blue, alpha).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SevenStarGuidanceEntity entity) {
        return CONSTELLATION;
    }
}
