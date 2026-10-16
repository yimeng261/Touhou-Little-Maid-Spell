package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PalmBlackHoleReleasePoseTest {
    @Test void switchAndThreeSecondHoldKeepTheLastVisibleWorldPosition() {
        Vector3d center = new Vector3d(104.25, 85.75, -208.5);
        var pose = new PalmBlackHoleReleasePose(259.9F, center, new Matrix4f().rotateXYZ(0.1F, 1, 0.2F).scale(2));
        // A relocation packet changes the ground entity base, never the held black hole.
        for (Vector3d landing : new Vector3d[]{new Vector3d(104, 64.04, -209), new Vector3d(100, 70.04, -200)}) {
            for (float age : new float[]{260, 260.5F, 280, 319.9F, 320}) {
                assertEquals(center, pose.centerAt(age, landing));
            }
        }
    }

    @Test void fallReachesTheAuthoritativeLandingOnRaisedAndLowerTerrain() {
        Vector3d center = new Vector3d(4.1, 90, -6.2);
        var pose = new PalmBlackHoleReleasePose(259.99F, center, new Matrix4f().scale(2));
        for (double ground : new double[]{64.04, 80.04, -40.96}) {
            Vector3d landing = new Vector3d(4, ground, -6);
            double previousY = center.y;
            for (int tick = 320; tick <= 340; tick++) {
                Vector3d next = pose.centerAt(tick, landing);
                assertTrue(next.y <= previousY && next.y >= ground);
                previousY = next.y;
            }
            assertEquals(landing, pose.centerAt(340, landing));
            assertEquals(landing, pose.centerAt(341, landing));
            assertEquals(center.y * 0.75 + ground * 0.25, pose.centerAt(330, landing).y, 1.0E-8);
        }
    }

    @Test void changingRenderersPreservesOrientationAndSizeForEveryModelVertex() {
        Matrix4f locator = new Matrix4f().rotateY(1.2F).rotateZ(-0.3F).scale(2);
        var pose = new PalmBlackHoleReleasePose(259.9F, new Vector3d(), locator);
        Matrix4f detached = new Matrix4f().rotate(pose.rotation()).scale(pose.scale());
        for (Vector3f vertex : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
            assertTrue(locator.transformPosition(new Vector3f(vertex))
                    .distance(detached.transformPosition(new Vector3f(vertex))) < 1.0E-5F);
        }
        locator.zero();
        pose.scale().zero();
        pose.rotation().identity();
        assertEquals(2, pose.scale().x);
    }

    @Test void staleBuildupPoseIsRejectedWhenTheCameraReturnsAfterRelease() {
        assertFalse(new PalmBlackHoleReleasePose(120, new Vector3d(), new Matrix4f().scale(0.1F)).isNearRelease());
        assertTrue(new PalmBlackHoleReleasePose(259.5F, new Vector3d(), new Matrix4f().scale(2)).isNearRelease());
    }
}
