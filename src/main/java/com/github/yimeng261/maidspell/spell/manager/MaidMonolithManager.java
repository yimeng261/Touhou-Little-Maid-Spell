package com.github.yimeng261.maidspell.spell.manager;

import com.Polarice3.Goety.common.entities.hostile.servants.ObsidianMonolith;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆黑曜石巨柱管理器
 * 追踪每个女仆拥有的巨柱，并限制数量
 * 支持晋升之环（1个巨柱）和终末之环（5个巨柱）
 */
public class MaidMonolithManager {
    private static final int MAX_MONOLITHS_PER_MAID = 5;

    // 使用 UUID 作为 key 来追踪女仆的巨柱列表
    private static final Map<UUID, List<ObsidianMonolith>> MAID_MONOLITHS = new ConcurrentHashMap<>();

    /**
     * 添加巨柱到女仆的列表（使用默认最大数量）
     * 如果超过最大数量，会移除最旧的巨柱
     */
    public static void addMonolith(EntityMaid maid, ObsidianMonolith monolith) {
        addMonolith(maid, monolith, MAX_MONOLITHS_PER_MAID);
    }

    /**
     * 添加巨柱到女仆的列表（指定最大数量）
     * 如果超过最大数量，会移除最旧的巨柱
     *
     * @param maid 女仆实体
     * @param monolith 巨柱实体
     * @param maxCount 最大巨柱数量（晋升之环=1，终末之环=5）
     */
    public static void addMonolith(EntityMaid maid, ObsidianMonolith monolith, int maxCount) {
        UUID maidUUID = maid.getUUID();
        List<ObsidianMonolith> monoliths = MAID_MONOLITHS.computeIfAbsent(maidUUID, k -> new ArrayList<>());

        // 清理已死亡的巨柱
        monoliths.removeIf(m -> !m.isAlive());

        // 如果达到上限，移除最旧的巨柱
        if (monoliths.size() >= maxCount) {
            ObsidianMonolith oldest = monoliths.remove(0);
            if (oldest.isAlive()) {
                oldest.discard();
            }
        }

        monoliths.add(monolith);
    }

    /**
     * 获取女仆当前拥有的巨柱数量（只计算存活的）
     */
    public static int getMonolithCount(EntityMaid maid) {
        UUID maidUUID = maid.getUUID();
        List<ObsidianMonolith> monoliths = MAID_MONOLITHS.get(maidUUID);

        if (monoliths == null) {
            return 0;
        }

        // 清理已死亡的巨柱
        monoliths.removeIf(m -> !m.isAlive());

        return monoliths.size();
    }

    /**
     * 获取女仆的所有存活巨柱
     */
    public static List<ObsidianMonolith> getMonoliths(EntityMaid maid) {
        UUID maidUUID = maid.getUUID();
        List<ObsidianMonolith> monoliths = MAID_MONOLITHS.get(maidUUID);

        if (monoliths == null) {
            return Collections.emptyList();
        }

        // 清理已死亡的巨柱
        monoliths.removeIf(m -> !m.isAlive());

        return new ArrayList<>(monoliths);
    }

    /**
     * 移除女仆的巨柱记录
     */
    public static void removeMonolith(EntityMaid maid, ObsidianMonolith monolith) {
        UUID maidUUID = maid.getUUID();
        List<ObsidianMonolith> monoliths = MAID_MONOLITHS.get(maidUUID);

        if (monoliths != null) {
            monoliths.remove(monolith);
        }
    }

    /**
     * 清理女仆的所有巨柱记录
     */
    public static void clearMaidMonoliths(EntityMaid maid) {
        MAID_MONOLITHS.remove(maid.getUUID());
    }

    /**
     * 定期清理无效的记录（可以在服务器 tick 事件中调用）
     */
    public static void cleanup() {
        MAID_MONOLITHS.values().forEach(list -> list.removeIf(m -> !m.isAlive()));
        MAID_MONOLITHS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
