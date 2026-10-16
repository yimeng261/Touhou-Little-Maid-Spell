package com.github.yimeng261.maidspell.stagewright.support;

import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 循环里逐项比对时收集不一致，最后一次性断言为空，免得每一项都单独记一条检查。
 * <p>只保留前 {@value #LIMIT} 条明细，总数另记。
 */
public final class Mismatches {
    private static final int LIMIT = 20;

    private final List<String> details = new ArrayList<>();
    private int total;

    public void equal(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            add(label + "：期望 " + expected + "，实际 " + actual);
        }
    }

    public void close(String label, double expected, double actual, double tolerance) {
        if (!(Math.abs(expected - actual) <= tolerance)) {
            add(label + "：期望 " + expected + "±" + tolerance + "，实际 " + actual);
        }
    }

    public void close(String label, Vec3 expected, Vec3 actual, double tolerance) {
        close(label + ".x", expected.x, actual.x, tolerance);
        close(label + ".y", expected.y, actual.y, tolerance);
        close(label + ".z", expected.z, actual.z, tolerance);
    }

    public void check(String label, boolean condition) {
        if (!condition) {
            add(label);
        }
    }

    private void add(String detail) {
        total++;
        if (details.size() < LIMIT) {
            details.add(detail);
        }
    }

    public void verify(SceneContext ctx, String label) {
        List<String> report = new ArrayList<>(details);
        if (total > details.size()) {
            report.add("……共 " + total + " 处");
        }
        ctx.check(report).as(label).isEmpty();
    }
}
