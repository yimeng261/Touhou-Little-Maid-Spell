package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.FoxLeafSurfaceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public class EntityMaidFoxLeafMovementMixin {
    @Inject(method = "isPushedByFluid()Z", at = @At("HEAD"), cancellable = true, remap = true)
    private void maidspell$disableFluidPushForMoltenFoxLeaf(CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.isUnderWater()) {
            return;
        }

        BlockPos pos = maid.blockPosition();
        FluidState feetFluid = maid.level().getFluidState(pos);
        FluidState belowFluid = maid.level().getFluidState(pos.below());
        boolean moltenFoxLeaf = BaubleStateManager.hasBauble(maid, MaidSpellItems.MOLTEN_FOX_LEAF);
        if (moltenFoxLeaf && (feetFluid.is(FluidTags.LAVA) || belowFluid.is(FluidTags.LAVA))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canTeleportTo(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$allowMoltenFoxLeafTeleport(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.MOLTEN_FOX_LEAF)) {
            return;
        }

        boolean lavaSurface = FoxLeafSurfaceHelper.isFluidSurface(maid.level(), pos, FluidTags.LAVA);
        if (!lavaSurface) {
            return;
        }

        BlockPos delta = pos.subtract(maid.blockPosition());
        cir.setReturnValue(maid.level().noCollision(maid, maid.getBoundingBox().move(delta)));
    }
}
