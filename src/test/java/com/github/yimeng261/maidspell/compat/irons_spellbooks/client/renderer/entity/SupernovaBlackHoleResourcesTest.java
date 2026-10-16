package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.core.Direction;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;
import software.bernie.geckolib.core.molang.MolangParser;
import software.bernie.geckolib.core.molang.MolangQueries;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupernovaBlackHoleResourcesTest {
    private static final Path ASSETS = Path.of(System.getProperty("maidspell.projectDir"),
            "src/main/resources/assets/touhou_little_maid_spell");

    @Test
    void geckoLibBakesTheEntireModelWithValidTextureCoordinates() throws IOException {
        BakedGeoModel model = loadModel();
        List<GeoBone> bones = new ArrayList<>();
        model.topLevelBones().forEach(bone -> collectBones(bone, bones));
        assertEquals(19, bones.size());
        assertEquals(604, bones.stream().mapToInt(bone -> bone.getCubes().size()).sum());
        GeoBone core = model.getBone("blackholecore").orElseThrow();
        assertEquals(0.0F, core.getPivotX());
        assertEquals(0.0F, core.getPivotY());
        assertEquals(0.0F, core.getPivotZ());
        for (GeoBone bone : bones) {
            for (var cube : bone.getCubes()) {
                boolean textured = false;
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    textured = true;
                    for (var vertex : quad.vertices()) {
                        assertTrue(vertex.position().isFinite(), bone.getName());
                        assertTrue(vertex.texU() >= 0 && vertex.texU() <= 1, bone.getName());
                        assertTrue(vertex.texV() >= 0 && vertex.texV() <= 1, bone.getName());
                    }
                }
                assertTrue(textured, bone.getName());
            }
        }
    }

    @Test
    void geckoLibLoadsTheLoopAndEveryAnimatedBoneExists() throws IOException {
        var json = JsonParser.parseString(read("animations/supernova_black_hole.animation.json"))
                .getAsJsonObject().getAsJsonObject("animations");
        Animation animation = JsonUtil.GEO_GSON.fromJson(json, BakedAnimations.class)
                .getAnimation("animation.supernova_black_hole.idle");
        assertNotNull(animation);
        assertEquals(0.08333D * 20, animation.length(), 1.0E-8D);
        assertEquals(Animation.LoopType.LOOP, animation.loopType());
        assertEquals(8, animation.boneAnimations().length);
        BakedGeoModel model = loadModel();
        for (var bone : animation.boneAnimations()) {
            assertTrue(model.getBone(bone.boneName()).isPresent(), bone.boneName());
        }
        var disk = java.util.Arrays.stream(animation.boneAnimations())
                .filter(bone -> bone.boneName().equals("blackholeclaub")).findFirst().orElseThrow();
        var spin = disk.rotationKeyFrames().yKeyframes();
        double turns = Math.abs(spin.get(spin.size() - 1).endValue().get()
                - spin.get(0).startValue().get()) / (2 * Math.PI);
        assertEquals(12.0D, turns / (animation.length() / 20), 0.001D);
    }

    @Test
    void diskUndersideHasOutwardNormalsAndMatchingTextureCoordinates() throws IOException {
        List<GeoBone> bones = new ArrayList<>();
        collectBones(loadModel().getBone("blackholeclaub").orElseThrow(), bones);
        int diskTiles = 0;
        for (GeoBone bone : bones) {
            for (var cube : bone.getCubes()) {
                diskTiles++;
                assertEquals(0.16D, cube.size().y, 1.0E-6D);
                GeoQuad up = cube.quads()[4];
                GeoQuad down = cube.quads()[5];
                assertNotNull(up);
                assertNotNull(down);
                assertEquals(1.0F, up.normal().y);
                assertEquals(-1.0F, down.normal().y);
                for (var top : up.vertices()) {
                    var bottom = java.util.Arrays.stream(down.vertices())
                            .filter(vertex -> vertex.position().x == top.position().x
                                    && vertex.position().z == top.position().z)
                            .findFirst().orElseThrow();
                    assertEquals(top.texU(), bottom.texU(), 1.0E-6F);
                    assertEquals(top.texV(), bottom.texV(), 1.0E-6F);
                    assertEquals(0.01F, top.position().y - bottom.position().y, 1.0E-6F);
                }
            }
        }
        assertEquals(64, diskTiles);
    }

    @Test
    void clippedDiskTilesDoNotStackGlowAtTheirSeams() throws IOException {
        List<GeoBone> bones = new ArrayList<>();
        collectBones(loadModel().getBone("blackholeclaub").orElseThrow(), bones);
        List<Area> originalTiles = new ArrayList<>();
        List<Area> clippedTiles = new ArrayList<>();
        for (GeoBone bone : bones) {
            for (var cube : bone.getCubes()) {
                originalTiles.add(horizontalArea(cube, Direction.UP));
                var clipped = SupernovaBlackHoleDiskMesh.clip(cube,
                        bone.getPivotX() / 16, bone.getPivotZ() / 16);
                Area area = horizontalArea(clipped, Direction.UP);
                assertTrue(areaSize(area) > 1.0E-5D, bone.getName());
                clippedTiles.add(area);
                Area undersideDifference = horizontalArea(clipped, Direction.DOWN);
                undersideDifference.exclusiveOr(area);
                assertTrue(areaSize(undersideDifference) < 1.0E-6D, bone.getName());
                assertClippedUvs(cube, clipped);
            }
        }
        assertEquals(64, clippedTiles.size());
        int originalOverlaps = 0;
        for (int i = 0; i < clippedTiles.size(); i++) {
            for (int j = i + 1; j < clippedTiles.size(); j++) {
                Area originalOverlap = new Area(originalTiles.get(i));
                originalOverlap.intersect(originalTiles.get(j));
                if (areaSize(originalOverlap) > 1.0E-5D) {
                    originalOverlaps++;
                }
                Area clippedOverlap = new Area(clippedTiles.get(i));
                clippedOverlap.intersect(clippedTiles.get(j));
                assertTrue(areaSize(clippedOverlap) < 1.0E-6D, "tiles " + i + ", " + j);
            }
        }
        assertTrue(originalOverlaps >= 16, "The original geometry must reproduce the reported overlap");
    }

    private static void assertClippedUvs(software.bernie.geckolib.cache.object.GeoCube original,
                                         software.bernie.geckolib.cache.object.GeoCube clipped) {
        for (GeoQuad face : clipped.quads()) {
            GeoVertex[] source = original.quads()[face.direction() == Direction.UP ? 4 : 5].vertices();
            Vector3f edgeU = new Vector3f(source[1].position()).sub(source[0].position());
            Vector3f edgeV = new Vector3f(source[3].position()).sub(source[0].position());
            for (GeoVertex vertex : face.vertices()) {
                Vector3f offset = new Vector3f(vertex.position()).sub(source[0].position());
                float u = offset.dot(edgeU) / edgeU.lengthSquared();
                float v = offset.dot(edgeV) / edgeV.lengthSquared();
                assertTrue(u >= -1.0E-5F && u <= 1.00001F);
                assertTrue(v >= -1.0E-5F && v <= 1.00001F);
                assertEquals(source[0].texU() + u * (source[1].texU() - source[0].texU())
                        + v * (source[3].texU() - source[0].texU()), vertex.texU(), 1.0E-6F);
                assertEquals(source[0].texV() + u * (source[1].texV() - source[0].texV())
                        + v * (source[3].texV() - source[0].texV()), vertex.texV(), 1.0E-6F);
            }
        }
    }

    private static Area horizontalArea(software.bernie.geckolib.cache.object.GeoCube cube, Direction side) {
        var pivot = cube.pivot();
        var rotation = cube.rotation();
        Matrix4f transform = new Matrix4f()
                .translate((float) pivot.x / 16, (float) pivot.y / 16, (float) pivot.z / 16)
                .rotateZ((float) rotation.z).rotateY((float) rotation.y).rotateX((float) rotation.x)
                .translate((float) -pivot.x / 16, (float) -pivot.y / 16, (float) -pivot.z / 16);
        Area area = new Area();
        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.direction() != side) {
                continue;
            }
            Path2D.Float polygon = new Path2D.Float();
            for (int i = 0; i < quad.vertices().length; i++) {
                Vector3f point = transform.transformPosition(new Vector3f(quad.vertices()[i].position()));
                if (i == 0) {
                    polygon.moveTo(point.x, point.z);
                } else {
                    polygon.lineTo(point.x, point.z);
                }
            }
            polygon.closePath();
            area.add(new Area(polygon));
        }
        return area;
    }

    private static double areaSize(Area area) {
        double total = 0, firstX = 0, firstY = 0, previousX = 0, previousY = 0;
        double[] point = new double[6];
        for (PathIterator iterator = area.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            int type = iterator.currentSegment(point);
            if (type == PathIterator.SEG_MOVETO) {
                firstX = previousX = point[0];
                firstY = previousY = point[1];
            } else if (type == PathIterator.SEG_LINETO) {
                total += previousX * point[1] - point[0] * previousY;
                previousX = point[0];
                previousY = point[1];
            } else if (type == PathIterator.SEG_CLOSE) {
                total += previousX * firstY - firstX * previousY;
            }
        }
        return Math.abs(total) / 2;
    }

    @Test
    void diskAnimationRetainsTheSourceTiltTremble() throws IOException {
        var json = JsonParser.parseString(read("animations/supernova_black_hole.animation.json"))
                .getAsJsonObject().getAsJsonObject("animations");
        Animation animation = JsonUtil.GEO_GSON.fromJson(json, BakedAnimations.class)
                .getAnimation("animation.supernova_black_hole.idle");
        var disk = java.util.Arrays.stream(animation.boneAnimations())
                .filter(bone -> bone.boneName().equals("blackholeclaub")).findFirst().orElseThrow();
        var values = List.of(disk.rotationKeyFrames().xKeyframes().get(0).endValue(),
                disk.rotationKeyFrames().zKeyframes().get(0).endValue(),
                disk.scaleKeyFrames().xKeyframes().get(0).endValue());
        double previousTime = MolangParser.INSTANCE.getVariable(MolangQueries.ANIM_TIME).get();
        double previousLifeTime = MolangParser.INSTANCE.getVariable(MolangQueries.LIFE_TIME).get();
        double[] time = {0};
        MolangParser.INSTANCE.setValue(MolangQueries.ANIM_TIME, () -> time[0] % (animation.length() / 20));
        MolangParser.INSTANCE.setValue(MolangQueries.LIFE_TIME, () -> time[0]);
        try {
            for (int axis = 0; axis < values.size(); axis++) {
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                int crossings = 0;
                double previous = 0;
                for (int frame = 0; frame <= 240; frame++) {
                    time[0] = frame / 120.0D;
                    double value = values.get(axis).get();
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    double offset = value - (axis == 2 ? 1 : 0);
                    if (previous * offset < 0) {
                        crossings++;
                    }
                    if (Math.abs(offset) > 1.0E-7D) {
                        previous = offset;
                    }
                }
                assertTrue(crossings >= 16, "Tremble must keep changing while the spin loop resets");
                if (axis == 2) {
                    assertTrue(min >= 0.92 && max <= 1.08);
                    assertTrue(max - min > 0.08);
                } else {
                    assertTrue(min >= -2.000001 && max <= 2.000001);
                    assertTrue(max - min > 2.0);
                }
            }
        } finally {
            MolangParser.INSTANCE.setValue(MolangQueries.ANIM_TIME, () -> previousTime);
            MolangParser.INSTANCE.setValue(MolangQueries.LIFE_TIME, () -> previousLifeTime);
        }
    }

    @Test
    void texturePreservesTransparentSpaceAndOpaqueBlackCore() throws IOException {
        BufferedImage texture = ImageIO.read(ASSETS.resolve(
                "textures/entity/companion_black_hole/supernova.png").toFile());
        assertNotNull(texture);
        assertEquals(1024, texture.getWidth());
        assertEquals(1024, texture.getHeight());
        boolean transparent = false;
        boolean black = false;
        boolean purple = false;
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int pixel = texture.getRGB(x, y);
                transparent |= (pixel >>> 24) == 0;
                black |= pixel == 0xFF000000;
                purple |= pixel == 0xFF8244FF;
            }
        }
        assertTrue(transparent && black && purple);
    }

    @Test
    void culledCorePresentsBlackInsteadOfItsInwardPurpleFaces() throws IOException {
        List<GeoBone> bones = new ArrayList<>();
        collectBones(loadModel().getBone("blackholecore").orElseThrow(), bones);
        List<TexturedTriangle> triangles = new ArrayList<>();
        for (GeoBone bone : bones) {
            for (var cube : bone.getCubes()) {
                var pivot = cube.pivot();
                var rotation = cube.rotation();
                Matrix4f transform = new Matrix4f()
                        .translate((float) pivot.x / 16, (float) pivot.y / 16, (float) pivot.z / 16)
                        .rotateZ((float) rotation.z).rotateY((float) rotation.y).rotateX((float) rotation.x)
                        .translate((float) -pivot.x / 16, (float) -pivot.y / 16, (float) -pivot.z / 16);
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    GeoVertex[] vertices = quad.vertices();
                    for (int[] indices : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                        Vector3f[] positions = new Vector3f[3];
                        GeoVertex[] textureCoordinates = new GeoVertex[3];
                        for (int i = 0; i < 3; i++) {
                            textureCoordinates[i] = vertices[indices[i]];
                            positions[i] = transform.transformPosition(
                                    new Vector3f(textureCoordinates[i].position()));
                        }
                        triangles.add(new TexturedTriangle(positions, textureCoordinates));
                    }
                }
            }
        }
        BufferedImage texture = ImageIO.read(ASSETS.resolve(
                "textures/entity/companion_black_hole/supernova.png").toFile());
        boolean exposedPurpleBackface = false;
        for (int elevation : new int[]{-45, 0, 45, 80}) {
            for (int azimuth = 0; azimuth < 360; azimuth += 45) {
                double pitch = Math.toRadians(elevation);
                double yaw = Math.toRadians(azimuth);
                Vector3f camera = new Vector3f((float) (Math.cos(pitch) * Math.sin(yaw)),
                        (float) Math.sin(pitch), (float) (Math.cos(pitch) * Math.cos(yaw))).mul(4);
                int culled = sampleCore(triangles, texture, camera, true);
                assertEquals(0xFF000000, culled, "elevation=" + elevation + ", azimuth=" + azimuth);
                int doubleSided = sampleCore(triangles, texture, camera, false);
                exposedPurpleBackface |= doubleSided != 0xFF000000;
            }
        }
        assertTrue(exposedPurpleBackface, "The regression requires a visible inward-facing surface");
    }

    private static int sampleCore(List<TexturedTriangle> triangles, BufferedImage texture,
                                  Vector3f camera, boolean cull) {
        Vector3f direction = new Vector3f(camera).negate().normalize();
        float nearest = Float.POSITIVE_INFINITY;
        int color = 0;
        for (TexturedTriangle triangle : triangles) {
            Vector3f[] p = triangle.positions();
            float distance = cull
                    ? Intersectionf.intersectRayTriangleFront(camera, direction, p[0], p[1], p[2], 1.0E-6F)
                    : Intersectionf.intersectRayTriangle(camera, direction, p[0], p[1], p[2], 1.0E-6F);
            if (distance <= 0 || distance >= nearest) {
                continue;
            }
            Vector3f edge1 = new Vector3f(p[1]).sub(p[0]);
            Vector3f edge2 = new Vector3f(p[2]).sub(p[0]);
            Vector3f hit = new Vector3f(direction).mul(distance).add(camera).sub(p[0]);
            float aa = edge1.dot(edge1), ab = edge1.dot(edge2), bb = edge2.dot(edge2);
            float denominator = aa * bb - ab * ab;
            float v = (bb * hit.dot(edge1) - ab * hit.dot(edge2)) / denominator;
            float w = (aa * hit.dot(edge2) - ab * hit.dot(edge1)) / denominator;
            GeoVertex[] uv = triangle.textureCoordinates();
            float uTex = (1 - v - w) * uv[0].texU() + v * uv[1].texU() + w * uv[2].texU();
            float vTex = (1 - v - w) * uv[0].texV() + v * uv[1].texV() + w * uv[2].texV();
            int pixel = texture.getRGB(Math.max(0, Math.min(texture.getWidth() - 1,
                            (int) (uTex * texture.getWidth()))),
                    Math.max(0, Math.min(texture.getHeight() - 1, (int) (vTex * texture.getHeight()))));
            if ((pixel >>> 24) < 26) {
                continue;
            }
            nearest = distance;
            color = pixel;
        }
        return color;
    }

    private record TexturedTriangle(Vector3f[] positions, GeoVertex[] textureCoordinates) {
    }

    private static BakedGeoModel loadModel() throws IOException {
        Model model = JsonUtil.GEO_GSON.fromJson(read("geo/supernova_black_hole.geo.json"), Model.class);
        return BakedModelFactory.getForNamespace("touhou_little_maid_spell")
                .constructGeoModel(GeometryTree.fromModel(model));
    }

    private static String read(String path) throws IOException {
        return Files.readString(ASSETS.resolve(path), StandardCharsets.UTF_8);
    }

    private static void collectBones(GeoBone bone, List<GeoBone> result) {
        result.add(bone);
        bone.getChildBones().forEach(child -> collectBones(child, result));
    }
}
