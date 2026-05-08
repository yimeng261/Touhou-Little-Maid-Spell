package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityMaid.class, remap = false)
public class EntityMaidAnchorCoreMixin {
    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = true)
    private void maidspell$blockIllegalAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        try {
            EntityMaid maid = (EntityMaid) (Object) this;
            if (AnchorCoreProtection.shouldBlockMaidSerialization(maid, compound, "addAdditionalSaveData")) {
                ci.cancel();
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid addAdditionalSaveData source", e);
        }
    }

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = true)
    private void maidspell$blockUnexpectedRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        try {
            EntityMaid maid = (EntityMaid) (Object) this;
            Global.LOGGER.debug("remove called for {}", maid);
            if (AnchorCoreProtection.shouldBlockMaidRemove(maid, reason, "remove")) {
                ci.cancel();
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid removal source", e);
        }
    }

    @Inject(method = "dropEquipment()V",
            at = @At("HEAD"),
            cancellable = true,
            remap = true)
    private void maidspell$preventAliveAnchoredMaidTombstone(CallbackInfo ci) {
        try {
            EntityMaid maid = (EntityMaid) (Object) this;
            if (AnchorCoreProtection.shouldBlockAliveAnchoredDrop(maid)) {
                Global.LOGGER.debug("Prevented alive maid {} from generating tombstone with health {} (anchor_core protection)",
                        maid.getUUID(), maid.getHealth());
                ci.cancel();
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid tombstone generation", e);
        }
    }
}
