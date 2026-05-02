package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.mixin.accessor.JumpControlAccessor;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Mixin用于移除女仆容器的距离检查限制
 * 允许玩家在任意距离打开女仆背包
 */
@Mixin(value = AbstractMaidContainer.class, remap = false)
public class AbstractMaidContainerMixin {

    @Final
    @Shadow
    protected EntityMaid maid;

    /**
     * 注入到 stillValid 方法开头，取消原方法并返回我们的逻辑
     */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = true)
    public void stillValid(Player playerIn, CallbackInfoReturnable<Boolean> cir) {
        if (maid == null) {
            return;
        }
        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)) {
            boolean isValid = maid.isOwnedBy(playerIn) && !maid.isSleeping() && maid.isAlive();
            cir.setReturnValue(isValid);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), remap = true)
    private void maidspell$clearResidualMovementOnGuiClose(Player playerIn, CallbackInfo ci) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }

        maid.getNavigation().stop();
        maid.getNavigationManager().resetNavigation();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.setJumping(false);
        maid.setXxa(0.0F);
        maid.setYya(0.0F);
        maid.setZza(0.0F);
        maid.setSpeed(0.0F);
        if (maid.getJumpControl() instanceof JumpControlAccessor jumpAccessor) {
            jumpAccessor.maidspell$setJump(false);
        }
        maid.getMoveControl().setWantedPosition(maid.getX(), maid.getY(), maid.getZ(), 0.0D);
        Vec3 deltaMovement = maid.getDeltaMovement();
        maid.setDeltaMovement(0.0D, deltaMovement.y, 0.0D);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                    remap = true
            ),
            remap = true
    )
    @Nullable
    private Entity redirectGetEntity(Level level, int entityId) {
        Entity entity = level.getEntity(entityId);
        if (entity instanceof EntityMaid) {
            return entity;
        }

        try {
            for (EntityMaid activeMaid : Global.activeMaids) {
                if (activeMaid.getId() == entityId && BaubleStateManager.hasBauble(activeMaid, MaidSpellItems.ENDER_POCKET)) {
                    Global.LOGGER.debug("从 Global 缓存中找到远距离女仆: {} (ID: {})",
                            activeMaid.getName().getString(), entityId);
                    return activeMaid;
                }
            }
        } catch (Exception e) {
            Global.LOGGER.error("从 Global 缓存中查找末影腰包女仆时发生错误", e);
        }

        return entity;
    }
}
