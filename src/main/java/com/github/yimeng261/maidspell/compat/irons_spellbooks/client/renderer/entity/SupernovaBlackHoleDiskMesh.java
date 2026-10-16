package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.ArrayList;
import java.util.List;

/** Trim the model's overlapping rectangular tiles to adjacent 22.5-degree sectors. */
final class SupernovaBlackHoleDiskMesh {
    private static final double SECTOR_ANGLE = Math.PI / 8;

    private SupernovaBlackHoleDiskMesh() {
    }

    static GeoCube clip(GeoCube cube, float centerX, float centerZ) {
        Matrix4f transform = cubeTransform(cube);
        Matrix4f inverse = new Matrix4f(transform).invert();
        Vector3f tileCenter = new Vector3f();
        for (GeoVertex vertex : cube.quads()[4].vertices()) {
            tileCenter.add(transform.transformPosition(new Vector3f(vertex.position())));
        }
        tileCenter.mul(0.25F);
        double angle = Math.atan2(tileCenter.z - centerZ, tileCenter.x - centerX);
        angle = Math.round(angle / SECTOR_ANGLE) * SECTOR_ANGLE;
        double lower = angle - SECTOR_ANGLE / 2;
        double upper = angle + SECTOR_ANGLE / 2;
        List<GeoQuad> triangles = new ArrayList<>();
        for (GeoQuad face : cube.quads()) {
            if (face == null) {
                continue;
            }
            List<GeoVertex> polygon = new ArrayList<>();
            for (GeoVertex vertex : face.vertices()) {
                polygon.add(new GeoVertex(transform.transformPosition(new Vector3f(vertex.position())),
                        vertex.texU(), vertex.texV()));
            }
            polygon = clipPlane(polygon, centerX, centerZ, -Math.sin(lower), Math.cos(lower));
            polygon = clipPlane(polygon, centerX, centerZ, Math.sin(upper), -Math.cos(upper));
            for (int i = 1; i + 1 < polygon.size(); i++) {
                GeoVertex a = localVertex(polygon.get(0), inverse);
                GeoVertex b = localVertex(polygon.get(i), inverse);
                GeoVertex c = localVertex(polygon.get(i + 1), inverse);
                // GeckoLib submits QUADS; a repeated last vertex represents one triangle.
                triangles.add(new GeoQuad(new GeoVertex[]{a, b, c, c}, face.normal(), face.direction()));
            }
        }
        return new GeoCube(triangles.toArray(GeoQuad[]::new), cube.pivot(), cube.rotation(),
                cube.size(), cube.inflate(), cube.mirror());
    }

    private static List<GeoVertex> clipPlane(List<GeoVertex> polygon, float centerX, float centerZ,
                                            double normalX, double normalZ) {
        List<GeoVertex> result = new ArrayList<>();
        if (polygon.isEmpty()) {
            return result;
        }
        GeoVertex previous = polygon.get(polygon.size() - 1);
        double previousDistance = planeDistance(previous, centerX, centerZ, normalX, normalZ);
        for (GeoVertex current : polygon) {
            double distance = planeDistance(current, centerX, centerZ, normalX, normalZ);
            if ((previousDistance >= 0) != (distance >= 0)) {
                float t = (float) (previousDistance / (previousDistance - distance));
                result.add(new GeoVertex(new Vector3f(previous.position()).lerp(current.position(), t),
                        previous.texU() + (current.texU() - previous.texU()) * t,
                        previous.texV() + (current.texV() - previous.texV()) * t));
            }
            if (distance >= 0) {
                result.add(current);
            }
            previous = current;
            previousDistance = distance;
        }
        return result;
    }

    private static double planeDistance(GeoVertex vertex, float centerX, float centerZ,
                                         double normalX, double normalZ) {
        return (vertex.position().x - centerX) * normalX + (vertex.position().z - centerZ) * normalZ;
    }

    private static GeoVertex localVertex(GeoVertex vertex, Matrix4f inverse) {
        return new GeoVertex(inverse.transformPosition(new Vector3f(vertex.position())),
                vertex.texU(), vertex.texV());
    }

    private static Matrix4f cubeTransform(GeoCube cube) {
        var pivot = cube.pivot();
        var rotation = cube.rotation();
        return new Matrix4f()
                .translate((float) pivot.x / 16, (float) pivot.y / 16, (float) pivot.z / 16)
                .rotateZ((float) rotation.z).rotateY((float) rotation.y).rotateX((float) rotation.x)
                .translate((float) -pivot.x / 16, (float) -pivot.y / 16, (float) -pivot.z / 16);
    }
}
