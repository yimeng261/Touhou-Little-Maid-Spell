package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoCube;

import java.util.List;

/** 给场景测试用的同包访问口：剑阵网格烘焙与剔除、七星指引的线框绘制、超新星黑洞吸积盘裁剪都是包内可见。 */
public final class RendererTestAccess {
    private RendererTestAccess() {
    }

    /** 烘焙后的一个面：每个顶点依次是 x、y、z、u、v。 */
    public record Face(float[] vertices, float nx, float ny, float nz) {
    }

    /** 剑阵缓存的剑身网格。 */
    public static final class SwordMesh {
        private final List<SwordArrayRenderer.Face> faces;

        public SwordMesh(BakedGeoModel model) {
            this.faces = SwordArrayRenderer.bakeMesh(model);
        }

        public List<Face> faces() {
            return faces.stream().map(face -> new Face(face.vertices(), face.nx(), face.ny(), face.nz())).toList();
        }

        public AABB bounds() {
            return SwordArrayRenderer.meshBounds(faces);
        }
    }

    public static Quaternionf swordRotation(Vec3 velocity, float roll) {
        return SwordArrayRenderer.rotation(velocity, roll, new Quaternionf(), new Vector3f());
    }

    public static AABB swordBounds(AABB meshBounds, Quaternionf rotation, Vec3 position) {
        return SwordArrayRenderer.transformedBounds(meshBounds, rotation, position);
    }

    public static void drawLockBrackets(MultiBufferSource buffers, Matrix4f matrix, Vec3 center,
                                        float radius, float rotation, float alpha) {
        SevenStarGuidanceRenderer.drawLockBrackets(buffers, matrix, center, radius, rotation, alpha);
    }

    public static void drawRing(MultiBufferSource buffers, Matrix4f matrix, Vec3 center, float radius, float rotation,
                                float red, float green, float blue, float alpha, int segments) {
        SevenStarGuidanceRenderer.drawRing(buffers, matrix, center, radius, rotation, red, green, blue, alpha, segments);
    }

    public static void drawReticle(MultiBufferSource buffers, Matrix4f matrix, Vec3 center, float radius, float alpha) {
        SevenStarGuidanceRenderer.drawReticle(buffers, matrix, center, radius, alpha);
    }

    public static void drawFinalConstellation(Vec3 center, Vec3 right, Vec3 up, Vec3 forward, double scale, int mask,
                                              int stage, float age, Matrix4f matrix, MultiBufferSource buffers) {
        SevenStarGuidanceRenderer.drawFinalConstellation(center, right, up, forward, scale, mask, stage, age, matrix, buffers);
    }

    public static GeoCube clipDiskTile(GeoCube cube, float centerX, float centerZ) {
        return SupernovaBlackHoleDiskMesh.clip(cube, centerX, centerZ);
    }
}
