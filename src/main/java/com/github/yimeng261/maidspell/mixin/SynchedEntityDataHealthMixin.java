package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.github.yimeng261.maidspell.utils.MaidDamageProcessor;
import com.github.yimeng261.maidspell.utils.MaidHealthWriteGuard;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataHealthMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private SyncedDataHolder entity;

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private <T> void maidspell$protectDirectMaidHealthWrites(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        if (MaidHealthWriteGuard.isBypassing()) {
            return;
        }
        if (!maidspell$isHealthAccessor(accessor)) {
            return;
        }
        if (!(value instanceof Float requestedHealth)) {
            return;
        }
        if (!(this.entity instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }

        float currentHealth = maid.getHealth();
        if (requestedHealth >= currentHealth) {
            return;
        }

        if (!MaidDamageProcessor.isCommonHurt(requestedHealth, maid)) {
            ci.cancel();
            return;
        }

        DataItem dataItem = new DataItem(maid, currentHealth - requestedHealth);
        MaidDamageProcessor.processSoulBook(dataItem);
        MaidDamageProcessor.applyBaubleHandlers(dataItem, maid);

        if (dataItem.isCanceled()) {
            dataItem.setAmount(0.0f);
        }

        float finalHealth = Math.max(0.0f, currentHealth - dataItem.getAmount());
        ci.cancel();
        MaidHealthWriteGuard.runBypassing(() -> maid.getEntityData().set(maidspell$healthAccessor(), finalHealth, force));
    }

    @Unique
    private static EntityDataAccessor<Float> maidspell$healthAccessor() {
        return LivingEntityHealthAccessor.maidspell$getHealthAccessor();
    }

    @Unique
    private static boolean maidspell$isHealthAccessor(EntityDataAccessor<?> accessor) {
        return accessor == maidspell$healthAccessor();
    }


}
