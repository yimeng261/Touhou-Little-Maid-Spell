package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.MagicalWinefoxBossEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 万法酒狐在附近时不许睡觉。
 *
 * <p>原版这条规则由 {@code Monster.isPreventingPlayerRest()} 提供，但酒狐改继承
 * {@code AbstractSpellCastingMob}（其父类是 {@code PathfinderMob}）之后就丢了——而且补不回来：
 * 那个方法只声明在 {@code Monster} 上，{@code LivingEntity} 根本没有；更关键的是
 * {@code ServerPlayer.startSleepInBed} 里那句查询写死了 {@code getEntitiesOfClass(Monster.class, …)}，
 * 就算能重写也扫不到我们。所以改用 Forge 的 {@link PlayerSleepInBedEvent} 从外面拦。
 *
 * <p>判定盒照抄原版：以床方块的 {@link Vec3#atBottomCenterOf} 为中心，XZ ±8、Y ±5，创造模式跳过。
 */
public final class WinefoxBossSleepGuard {

    /** 原版 {@code ServerPlayer.startSleepInBed} 里写死的两个半径。 */
    private static final double HORIZONTAL_RANGE = 8.0D;
    private static final double VERTICAL_RANGE = 5.0D;

    private WinefoxBossSleepGuard() {
    }

    @SubscribeEvent
    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (event.getResultStatus() != null) {
            // 已经有别的原因不让睡了，不必再判。
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide || player.isCreative()) {
            return;
        }
        BlockPos bed = event.getPos();
        if (bed == null) {
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(bed);
        AABB area = new AABB(
                center.x - HORIZONTAL_RANGE, center.y - VERTICAL_RANGE, center.z - HORIZONTAL_RANGE,
                center.x + HORIZONTAL_RANGE, center.y + VERTICAL_RANGE, center.z + HORIZONTAL_RANGE);
        boolean bossNearby = !player.level()
                .getEntitiesOfClass(MagicalWinefoxBossEntity.class, area, LivingEntity::isAlive)
                .isEmpty();
        if (bossNearby) {
            event.setResult(Player.BedSleepingProblem.NOT_SAFE);
        }
    }
}
