package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** 世界坐标快照，让黑洞从手部图层交接给实体渲染器时不会跳变。 */
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
        // 蓄力期间视线移开，也不能沿用旧的、较小的手部姿态。
        return age >= PalmBlackHoleTimeline.TOP_TICK - 2 && age < PalmBlackHoleTimeline.TOP_TICK;
    }

    public Vector3d centerAt(float age, Vector3d landing) {
        double fall = 1 - PalmBlackHoleTimeline.dropHeight(age, 1);
        if (fall <= 0) return new Vector3d(center);
        if (fall >= 1) return new Vector3d(landing);
        // 先定格在最后一次可见的准确中心，再抵达服务端的地形锚点。
        // 动画与网络之间的细小偏差在下落过程中平滑修正，不会出现跳变。
        return new Vector3d(center).lerp(landing, fall);
    }

    public Quaternionf rotation() { return new Quaternionf(rotation); }
    public Vector3f scale() { return new Vector3f(scale); }
}
