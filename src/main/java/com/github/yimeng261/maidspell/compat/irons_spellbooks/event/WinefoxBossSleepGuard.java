package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** 酒狐不是 Monster，原版睡眠检查不会找到她；用 NeoForge 事件恢复附近有敌时不可睡眠。 */
public final class WinefoxBossSleepGuard {

    /** 原版 {@code ServerPlayer.startSleepInBed} 里写死的两个半径。 */
    private static final double HORIZONTAL_RANGE = 8.0D;
    private static final double VERTICAL_RANGE = 5.0D;

    private WinefoxBossSleepGuard() {
    }

    @SubscribeEvent
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() != null) {
            // 已经有别的原因不让睡了，不必再判。
            return;
        }
        Player player = event.getEntity();
        if (player.isCreative()) {
            return;
        }
        BlockPos bed = event.getPos();
        Vec3 center = Vec3.atBottomCenterOf(bed);
        AABB area = new AABB(
                center.x - HORIZONTAL_RANGE, center.y - VERTICAL_RANGE, center.z - HORIZONTAL_RANGE,
                center.x + HORIZONTAL_RANGE, center.y + VERTICAL_RANGE, center.z + HORIZONTAL_RANGE);
        boolean bossNearby = !player.level()
                .getEntitiesOfClass(MagicalWinefoxBossEntity.class, area, boss -> !boss.isRemoved())
                .isEmpty();
        if (bossNearby) {
            event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
        }
    }
}
