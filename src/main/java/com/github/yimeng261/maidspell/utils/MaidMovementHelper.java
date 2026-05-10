package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.mixin.accessor.JumpControlAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

public final class MaidMovementHelper {
    private MaidMovementHelper() {}

    /**
     * 停止女仆的所有移动，清除导航、记忆和动量。
     * Y轴动量保留，交给水面行走逻辑维持。
     */
    public static void stopAllMovement(EntityMaid maid) {
        maid.getNavigation().stop();
        maid.getNavigationManager().resetNavigation();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.setJumping(false);
        maid.setXxa(0.0F);
        maid.setYya(0.0F);
        maid.setZza(0.0F);
        maid.setSpeed(0.0F);
        Vec3 delta = maid.getDeltaMovement();
        maid.setDeltaMovement(0.0D, delta.y, 0.0D);
        if (maid.getJumpControl() instanceof JumpControlAccessor jumpAccessor) {
            jumpAccessor.maidspell$setJump(false);
        }
        maid.getMoveControl().setWantedPosition(maid.getX(), maid.getY(), maid.getZ(), 0.0D);
    }
}
