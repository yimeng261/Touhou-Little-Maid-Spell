package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SwordArrayLayout;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SwordArrayRendererTest {
    @Test
    void cachedMeshRetainsEveryFaceTextureCoordinateAndFiniteNormalOfTheExistingModel() throws Exception {
        BakedGeoModel model = loadSwordModel();
        var mesh = SwordArrayRenderer.bakeMesh(model);
        int expected = model.topLevelBones().stream().flatMap(bone -> bone.getCubes().stream())
                .mapToInt(cube -> (int) java.util.Arrays.stream(cube.quads()).filter(java.util.Objects::nonNull).count())
                .sum();
        assertTrue(expected > 0);
        assertEquals(expected, mesh.size());
        var sourceFaces = model.topLevelBones().stream().flatMap(bone -> bone.getCubes().stream())
                .flatMap(cube -> java.util.Arrays.stream(cube.quads()))
                .filter(java.util.Objects::nonNull).toList();
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int faceIndex = 0; faceIndex < mesh.size(); faceIndex++) {
            var face = mesh.get(faceIndex);
            assertEquals(20, face.vertices().length);
            Vector3f normal = new Vector3f(face.nx(), face.ny(), face.nz());
            assertTrue(normal.isFinite());
            assertEquals(1.0F, normal.length(), 1.0E-5F);
            float[] data = face.vertices();
            for (int i = 0; i < data.length; i += 5) {
                assertTrue(new Vector3f(data[i], data[i + 1], data[i + 2]).isFinite());
                // Upstream UVs may wrap beyond [0, 1]; preserve them exactly.
                var original = sourceFaces.get(faceIndex).vertices()[i / 5];
                assertEquals(original.texU(), data[i + 3]);
                assertEquals(original.texV(), data[i + 4]);
                minY = Math.min(minY, data[i + 1]);
                maxY = Math.max(maxY, data[i + 1]);
            }
        }
        assertTrue(maxY - minY > 0.2F, "The sword mesh must have visible geometry");
        assertEquals(mesh.size(), SwordArrayRenderer.bakeMesh(model).size(), "Reload rebaking must be repeatable");
    }

    private BakedGeoModel loadSwordModel() throws Exception {
        var stream = getClass().getResourceAsStream("/assets/irons_spellbooks/geo/summoned_sword.geo.json");
        assertNotNull(stream);
        BakedGeoModel model;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Model raw = JsonUtil.GEO_GSON.fromJson(reader, Model.class);
            model = BakedModelFactory.getForNamespace("irons_spellbooks")
                    .constructGeoModel(GeometryTree.fromModel(raw));
        }
        return model;
    }

    @Test
    void cachedMeshUsesTheRestPoseEvenAfterAnotherRendererAnimatesTheSharedModel() throws Exception {
        BakedGeoModel model = loadSwordModel();
        var bone = model.topLevelBones().get(0);
        // GeckoLib saves this before a summoned weapon's animation changes the shared bone.
        bone.saveInitialSnapshot();
        var expected = SwordArrayRenderer.bakeMesh(model);
        float animatedRotX = bone.getRotX() + (float) Math.toRadians(90);
        bone.updateRotation(animatedRotX, 0.4F, -0.7F);
        bone.updatePosition(8, 13, -5.5F);
        bone.updateScale(0.8F, 1.2F, 0.9F);

        var actual = SwordArrayRenderer.bakeMesh(model);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i).vertices(), actual.get(i).vertices(), 1.0E-6F,
                    "The array must not cache another sword's swing pose");
            assertEquals(expected.get(i).nx(), actual.get(i).nx(), 1.0E-6F);
            assertEquals(expected.get(i).ny(), actual.get(i).ny(), 1.0E-6F);
            assertEquals(expected.get(i).nz(), actual.get(i).nz(), 1.0E-6F);
        }
        assertEquals(animatedRotX, bone.getRotX(), "Baking must preserve the other renderer's pose");
        assertEquals(0.4F, bone.getRotY());
        assertEquals(-0.7F, bone.getRotZ());
        assertEquals(13.0F, bone.getPosY());
        assertEquals(1.2F, bone.getScaleY());
    }

    @Test
    void actualSwordGeometryStaysMostlyVerticalForEveryRandomRoll() throws Exception {
        BakedGeoModel model = loadSwordModel();
        var bone = model.topLevelBones().get(0);
        bone.saveInitialSnapshot();
        bone.setRotX(bone.getRotX() + (float) Math.toRadians(90));
        var meshBounds = SwordArrayRenderer.meshBounds(SwordArrayRenderer.bakeMesh(model));
        for (long seed = 0; seed < 32; seed++) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, seed);
            for (var sword : layout.swords()) {
                for (double age : new double[]{sword.launchAge(), sword.landingAge()}) {
                    Quaternionf rotation = SwordArrayRenderer.rotation(sword.velocity(age), sword.roll(),
                            new Quaternionf(), new Vector3f());
                    var bounds = SwordArrayRenderer.transformedBounds(meshBounds, rotation, sword.position(age));
                    assertTrue(bounds.getYsize() > Math.max(bounds.getXsize(), bounds.getZsize()),
                            "The rendered blade must remain upright during flight and after landing");
                }
            }
        }
    }

    @Test
    void flightAndPlantedRotationsPointTheModelForwardAlongEachSwordTrajectory() {
        SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, 42);
        for (var sword : layout.swords()) {
            for (double age : new double[]{sword.launchAge(), sword.launchAge() + 2.5D, sword.landingAge()}) {
                Quaternionf rotation = SwordArrayRenderer.rotation(sword.velocity(age), sword.roll(),
                        new Quaternionf(), new Vector3f());
                Vector3f forward = rotation.transform(new Vector3f(0, 0, -1));
                var velocity = sword.velocity(age);
                Vector3f expected = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z).normalize();
                assertEquals(expected.x, forward.x, 1.0E-5F);
                assertEquals(expected.y, forward.y, 1.0E-5F);
                assertEquals(expected.z, forward.z, 1.0E-5F);
            }
        }
    }

    @Test
    void cullingBoundsContainEveryRenderedVertexDuringFlightAndAfterLanding() throws Exception {
        var mesh = SwordArrayRenderer.bakeMesh(loadSwordModel());
        var meshBounds = SwordArrayRenderer.meshBounds(mesh);
        for (long seed : new long[]{1, 42, 20261005}) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, seed);
            for (var sword : layout.swords()) {
                for (double age : new double[]{sword.launchAge() + 0.5D,
                        sword.landingAge() - 0.25D, sword.landingAge(), sword.expiryAge() - 0.5D}) {
                    Quaternionf rotation = SwordArrayRenderer.rotation(sword.velocity(age), sword.roll(),
                            new Quaternionf(), new Vector3f());
                    Vec3 position = sword.position(age);
                    var bounds = SwordArrayRenderer.transformedBounds(meshBounds, rotation, position);
                    for (var face : mesh) {
                        float[] data = face.vertices();
                        for (int i = 0; i < data.length; i += 5) {
                            Vector3f vertex = rotation.transform(new Vector3f(data[i], data[i + 1], data[i + 2]));
                            assertTrue(bounds.contains(vertex.x + position.x,
                                    vertex.y + position.y + SwordArrayLayout.SWORD_WIDTH * 0.5D,
                                    vertex.z + position.z), "Culling must not clip sword geometry");
                        }
                    }
                }
            }
        }
    }

    @Test
    void individualCullingSkipsOffscreenSwordsAndKeepsSwordsAtTheViewEdge() throws Exception {
        var meshBounds = SwordArrayRenderer.meshBounds(SwordArrayRenderer.bakeMesh(loadSwordModel()));
        Frustum frustum = new Frustum(new Matrix4f(),
                new Matrix4f().perspective((float) Math.toRadians(90), 1.0F, 0.1F, 100.0F));
        frustum.prepare(0, 0, 0);
        Quaternionf rotation = SwordArrayRenderer.rotation(new Vec3(0, -1, 0), 45,
                new Quaternionf(), new Vector3f());
        assertTrue(frustum.isVisible(SwordArrayRenderer.transformedBounds(meshBounds, rotation, new Vec3(0, 0, -5))));
        assertTrue(frustum.isVisible(SwordArrayRenderer.transformedBounds(meshBounds, rotation, new Vec3(5, 0, -5))));
        assertFalse(frustum.isVisible(SwordArrayRenderer.transformedBounds(meshBounds, rotation, new Vec3(30, 0, -5))));
        assertFalse(frustum.isVisible(SwordArrayRenderer.transformedBounds(meshBounds, rotation, new Vec3(0, 0, 5))));
    }

    @Test
    void stationarySwordRotationStaysFinite() {
        Quaternionf rotation = SwordArrayRenderer.rotation(Vec3.ZERO, 127,
                new Quaternionf(), new Vector3f());
        Vector3f forward = rotation.transform(new Vector3f(0, 0, -1));
        assertTrue(forward.isFinite());
        assertEquals(-1.0F, forward.y, 1.0E-5F);
    }
}
