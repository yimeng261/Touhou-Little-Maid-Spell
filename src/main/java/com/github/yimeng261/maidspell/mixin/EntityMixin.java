package com.github.yimeng261.maidspell.mixin;


import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IBossSyncedDataGuard;
import com.github.yimeng261.maidspell.api.IPersistentEncounterEntity;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import com.github.yimeng261.maidspell.utils.PersistentEntityLifecycleGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class,remap = false)
public class EntityMixin {

    @Inject(method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    private void maidspell$blockAnchoredMaidHardRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        try {
            Entity entity = (Entity) (Object) this;
            if (PersistentEntityLifecycleGuard.shouldBlockRemoval(entity, reason)) {
                ci.cancel();
                if (entity instanceof IPersistentEncounterEntity encounter) {
                    encounter.maidspell$onBlockedRemoval(reason);
                }
            }
        } catch (Exception e) {
            Global.LOGGER.error("[MaidSpell] Failed to check entity hard-removal protection", e);
        }
    }

    @Inject(method = "saveAsPassenger(Lnet/minecraft/nbt/CompoundTag;)Z",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onSaveAsPassenger(CompoundTag pCompound, CallbackInfoReturnable<Boolean> cir) {
        if (maidspell$checkAndBlockSerialization(pCompound, "saveAsPassenger")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Z",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onSave(CompoundTag pCompound, CallbackInfoReturnable<Boolean> cir) {
        if (maidspell$checkAndBlockSerialization(pCompound, "save")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onSaveWithoutId(CompoundTag pCompound, CallbackInfoReturnable<CompoundTag> cir) {
        if (maidspell$checkAndBlockSerialization(pCompound, "saveWithoutId")) {
            cir.setReturnValue(pCompound);
        }
    }

    /**
     * 注入 fireImmune()，当女仆装备不洁圣冠时返回 true
     * 与下界生物相同机制，从源头阻止着火
     */
    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true, remap = true)
    private void onFireImmune(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EntityMaid maid) {
            var unholyHat = MaidSpellItems.getUnholyHat();
            if (unholyHat != null && BaubleStateManager.hasBauble(maid, unholyHat)) {
                cir.setReturnValue(true);
            }
        }
    }

    private boolean maidspell$checkAndBlockSerialization(CompoundTag compound, String method) {
        try {
            return AnchorCoreProtection.shouldBlockEntitySerialization((Entity) (Object) this, compound, method);
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid {} source", method, e);
        }
        return false;
    }

    /**
     * 在 onSyncedDataUpdated 开头把新值转发给 {@link IBossSyncedDataGuard} 实现方，覆盖绕过 set() 直接修改同步字段的写入
     */
    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V",
            at = @At("HEAD"), remap = true)
    private void maidspell$observeSyncedDataUpdate(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof IBossSyncedDataGuard guard)) {
            return;
        }
        try {
            guard.maidspell$onSyncedDataUpdated(accessor, entity.getEntityData().get(accessor));
        } catch (Throwable throwable) {
            Global.LOGGER.error("Failed to observe synced data update for {}", entity.getUUID(), throwable);
        }
    }

}
