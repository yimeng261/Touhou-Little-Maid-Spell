package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.google.gson.JsonElement;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.Set;

/** 给场景测试用的同包访问口：剑阵碰撞索引和掌心黑洞定位点的关键帧采样都是包内可见。 */
public final class SpellTestAccess {
    private SpellTestAccess() {
    }

    public static Vector3f sampleLocator(JsonElement track, Vector3f fallback, double seconds) {
        return PalmBlackHoleLocatorPose.sample(track, fallback, seconds);
    }

    public static final class CollisionIndex<T> {
        private final SwordArrayCollisionIndex<T> index;

        public CollisionIndex(AABB bounds) {
            this.index = new SwordArrayCollisionIndex<>(bounds);
        }

        public void add(T value, AABB box) {
            index.add(value, box);
        }

        public Set<T> query(AABB box) {
            return index.query(box);
        }
    }
}
