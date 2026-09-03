package com.github.yimeng261.maidspell.utils;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 「这一片地方，女仆不许插手」。
 *
 * <p>驯服万法酒狐要求玩家单挑：挑战期间女仆不攻击、不喂食、不支援。
 * 判定得有个单一入口，散在各处各写一遍必然漏掉一处 —— 这个类就是那个入口。
 *
 * <p><b>为什么是静态区域表而不是直接问 Boss。</b>提出压制要求的是万法酒狐，
 * 而她继承铁魔法的 {@code AbstractSpellCastingMob}；女仆的战斗任务、饰品逻辑
 * 都属于本模组的核心部分，缺了铁魔法照样要跑。核心去 import 一个可选模组下的类，
 * 缺模组时就是 {@code NoClassDefFoundError}。所以反过来：Boss 单方面往这儿登记一片区域，
 * 核心那边只认坐标，谁登记的不关心。
 *
 * <p><b>区域会自己过期。</b>登记方每 tick 刷新一次；超过 {@link #EXPIRY_TICKS}
 * 没刷新就作废。区块卸载、实体被 {@code /kill}、服务端崩了重启 ——
 * 这些情况下没有任何人会来注销，靠过期兜住，免得留下一片永久压制区。
 */
public final class MaidSuppressionZone {

    /** 多久没刷新就当这片区域没了。2 秒，足够盖住一次卡顿。 */
    private static final int EXPIRY_TICKS = 40;

    private static final List<Zone> ZONES = new ArrayList<>();

    /** 上一次清过期是哪一 tick。同一 tick 里被问很多遍，扫一次就够。 */
    private static long lastSweepTick = Long.MIN_VALUE;

    private MaidSuppressionZone() {
    }

    /**
     * 登记 / 刷新一片压制区。同一个 owner 重复调即为续期。
     *
     * @param owner  登记者，用它的身份识别同一片区域
     * @param radius 半径（方块）
     */
    public static void refresh(Entity owner, double radius) {
        if (owner.level().isClientSide) {
            return;
        }
        ResourceKey<Level> dimension = owner.level().dimension();
        Vec3 center = owner.position();
        long now = owner.level().getGameTime();
        for (Zone zone : ZONES) {
            if (zone.ownerId == owner.getId() && zone.dimension.equals(dimension)) {
                zone.center = center;
                zone.radiusSqr = radius * radius;
                zone.lastRefresh = now;
                return;
            }
        }
        ZONES.add(new Zone(owner.getId(), dimension, center, radius * radius, now));
    }

    /**
     * 撤销某个登记者的压制区。过期机制之外的主动出口，用于"挑战结束"这种明确时刻。
     */
    public static void release(Entity owner) {
        // 登记方每 tick 都会调一次这个方法来"确认自己没在登记"，而表通常是空的。
        if (ZONES.isEmpty() || owner.level().isClientSide) {
            return;
        }
        ResourceKey<Level> dimension = owner.level().dimension();
        ZONES.removeIf(zone -> zone.ownerId == owner.getId() && zone.dimension.equals(dimension));
    }

    /**
     * 这个实体是不是正站在某片压制区里。
     *
     * <p>顺手清掉过期区域——没有别的地方会定时来扫，就着查询做最省事。
     */
    public static boolean suppresses(@Nullable Entity entity) {
        if (!isActive() || entity == null || entity.level().isClientSide) {
            return false;
        }
        long now = entity.level().getGameTime();
        if (now != lastSweepTick) {
            // 登记方每 tick 刷新一次，所以过期最多也就一 tick 扫一遍。
            lastSweepTick = now;
            ZONES.removeIf(zone -> now - zone.lastRefresh > EXPIRY_TICKS);
            if (ZONES.isEmpty()) {
                return false;
            }
        }
        ResourceKey<Level> dimension = entity.level().dimension();
        Vec3 position = entity.position();
        for (int index = 0; index < ZONES.size(); index++) {
            Zone zone = ZONES.get(index);
            if (zone.dimension.equals(dimension) && zone.center.distanceToSqr(position) <= zone.radiusSqr) {
                return true;
            }
        }
        return false;
    }

    /**
     * 现在世上有没有压制区。
     *
     * <p>调用方该拿它当第一道闸：这套东西挂在全服每一次伤害和每一次换目标上，
     * 而绝大多数存档里一片区域都没有。先问这一句，等于一次静态字段读。
     */
    public static boolean isActive() {
        return !ZONES.isEmpty();
    }

    /**
     * 服务端停止时清空。静态表跨存档存活会把上一个世界的区域带到下一个。
     */
    public static void clear() {
        ZONES.clear();
        lastSweepTick = Long.MIN_VALUE;
    }

    private static final class Zone {
        private final int ownerId;
        private final ResourceKey<Level> dimension;
        private Vec3 center;
        private double radiusSqr;
        private long lastRefresh;

        private Zone(int ownerId, ResourceKey<Level> dimension, Vec3 center, double radiusSqr, long lastRefresh) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.center = center;
            this.radiusSqr = radiusSqr;
            this.lastRefresh = lastRefresh;
        }
    }
}
