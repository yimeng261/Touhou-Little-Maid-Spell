package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 每 tick 的粗筛阶段；大型目标按包围盒而不是中心点建立索引。 */
final class SwordArrayCollisionIndex<T> {
    private static final double CELL_SIZE = 2.0D;
    private final Map<Long, List<T>> cells = new HashMap<>();
    private final AABB bounds;

    SwordArrayCollisionIndex(AABB bounds) {
        this.bounds = bounds;
    }

    void add(T value, AABB box) {
        if (!bounds.intersects(box)) return;
        visit(box, key -> cells.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value));
    }

    Set<T> query(AABB box) {
        Set<T> result = new LinkedHashSet<>();
        if (bounds.intersects(box)) {
            visit(box, key -> {
                List<T> bucket = cells.get(key);
                if (bucket != null) result.addAll(bucket);
            });
        }
        return result;
    }

    private void visit(AABB box, java.util.function.LongConsumer visitor) {
        int minX = cell(Math.max(bounds.minX, box.minX));
        int maxX = cell(Math.min(bounds.maxX, box.maxX));
        int minZ = cell(Math.max(bounds.minZ, box.minZ));
        int maxZ = cell(Math.min(bounds.maxZ, box.maxZ));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) visitor.accept(((long) x << 32) | (z & 0xffffffffL));
        }
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }
}
