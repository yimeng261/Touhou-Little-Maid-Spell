package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** A world-space snapshot survives the change from a hand layer to an entity renderer. */
public final class PalmBlackHoleReleasePose {
    private final float age;
    private final Vector3d center;
    private final Quaternionf rotation;
    private final Vector3f scale;

    public PalmBlackHoleReleasePose(float age, Vector3d center, Matrix4f transform) {
        this.age = age;
        this.center = new Vector3d(center);
        this.rotation = transform.getUnnormalizedRotation(new Quaternionf()).normalize();
        this.scale = transform.getScale(new Vector3f());
    }

    public boolean isNearRelease() {
        // Looking away during the buildup must not reuse an old, small hand pose.
        return age >= PalmBlackHoleTimeline.TOP_TICK - 2 && age < PalmBlackHoleTimeline.TOP_TICK;
    }

    public Vector3d centerAt(float age, Vector3d landing) {
        double fall = 1 - PalmBlackHoleTimeline.dropHeight(age, 1);
        if (fall <= 0) return new Vector3d(center);
        if (fall >= 1) return new Vector3d(landing);
        // Hold the exact last visible center, then reach the server's terrain anchor.
        // Small animation/network differences are corrected during the fall, without a jump.
        return new Vector3d(center).lerp(landing, fall);
    }

    public Quaternionf rotation() { return new Quaternionf(rotation); }
    public Vector3f scale() { return new Vector3f(scale); }
}
