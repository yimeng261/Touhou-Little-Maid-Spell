package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 酒狐不是 Monster，原版睡眠检查不会找到她；附近有她时床视为不安全。
 * 由 {@code ServerPlayerWinefoxSleepMixin} 在上床流程最前面调用，先于重生点写入与昼夜判断。
 */
public final class WinefoxBossSleepGuard {

    /** 原版 {@code ServerPlayer.startSleepInBed} 里写死的两个半径。 */
    private static final double HORIZONTAL_RANGE = 8.0D;
    private static final double VERTICAL_RANGE = 5.0D;

    private WinefoxBossSleepGuard() {
    }

    public static boolean isBossNearBed(Player player, BlockPos bed) {
        if (player.isCreative()) {
            return false;
        }
        Vec3 center = Vec3.atBottomCenterOf(bed);
        AABB area = new AABB(
                center.x - HORIZONTAL_RANGE, center.y - VERTICAL_RANGE, center.z - HORIZONTAL_RANGE,
                center.x + HORIZONTAL_RANGE, center.y + VERTICAL_RANGE, center.z + HORIZONTAL_RANGE);
        return !player.level()
                .getEntitiesOfClass(MagicalWinefoxBossEntity.class, area, boss -> !boss.isRemoved())
                .isEmpty();
    }
}
