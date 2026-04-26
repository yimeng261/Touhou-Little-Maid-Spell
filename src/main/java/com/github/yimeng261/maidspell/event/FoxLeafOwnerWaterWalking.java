package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.utils.FoxLeafOwnerEffectHelper;
import com.github.yimeng261.maidspell.utils.FoxLeafSurfaceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 在玩家自身 tick 末尾（Phase.END）检查附近女仆的狐叶饰品，
 * 对站在水/岩浆上的主人施加水面行走物理修正。
 * <p>
 * 必须在 PlayerTickEvent 而非女仆的 bauble onTick 中修正
 * 玩家位置，否则会被玩家自身的 travel() 覆盖。
 * <p>
 * 通过 MaidSpellMod 构造函数手动注册到 FORGE 总线。
 */
public class FoxLeafOwnerWaterWalking {

    private static final double MAX_SINK_SPEED = -0.05D;
    private static final double SURFACE_FLOAT_SPEED = 0.10D;
    private static final double SURFACE_Y_OFFSET = 0.9D;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.isSpectator() || player.isPassenger()) {
            return;
        }

        double range = Math.max(FoxLeafOwnerEffectHelper.rangeForFluid(FluidTags.WATER),
            FoxLeafOwnerEffectHelper.rangeForFluid(FluidTags.LAVA));
        for (EntityMaid maid : player.level().getEntitiesOfClass(
            EntityMaid.class, player.getBoundingBox().inflate(range))) {
            if (!maid.isAlive()) {
                continue;
            }

            if (FoxLeafOwnerEffectHelper.isValidSupportingMaid(
                player, maid, FluidTags.WATER, FoxLeafOwnerEffectHelper.rangeForFluid(FluidTags.WATER))) {
                if (!FoxLeafOwnerEffectHelper.canApplyOwnerSurfaceMotion(player, FluidTags.WATER)) {
                    break;
                }
                BlockPos pos = player.blockPosition();
                FluidState feet = player.level().getFluidState(pos);
                FluidState below = player.level().getFluidState(pos.below());
                if (feet.is(FluidTags.WATER) || below.is(FluidTags.WATER)) {
                    player.fallDistance = 0.0F;
                    FoxLeafSurfaceHelper.keepEntityAtFluidSurface(
                        player, pos, feet, below,
                        player.getDeltaMovement(), FluidTags.WATER,
                        MAX_SINK_SPEED, SURFACE_FLOAT_SPEED, SURFACE_Y_OFFSET
                    );                }
                break;
            }

            if (FoxLeafOwnerEffectHelper.isValidSupportingMaid(
                player, maid, FluidTags.LAVA, FoxLeafOwnerEffectHelper.rangeForFluid(FluidTags.LAVA))) {
                if (!FoxLeafOwnerEffectHelper.canApplyOwnerSurfaceMotion(player, FluidTags.LAVA)) {
                    break;
                }
                BlockPos pos = player.blockPosition();
                FluidState feet = player.level().getFluidState(pos);
                FluidState below = player.level().getFluidState(pos.below());
                if (feet.is(FluidTags.LAVA) || below.is(FluidTags.LAVA)) {
                    if (player.isOnFire()) {
                        player.clearFire();
                    }
                    player.fallDistance = 0.0F;
                    FoxLeafSurfaceHelper.keepEntityAtFluidSurface(
                        player, pos, feet, below,
                        player.getDeltaMovement(), FluidTags.LAVA,
                        MAX_SINK_SPEED, SURFACE_FLOAT_SPEED, 1.0D
                    );
                }
                break;
            }
        }
    }
}
