package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.utils.MaidDamageProcessor;
import com.github.yimeng261.maidspell.utils.MaidHealthWriteGuard;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataHealthMixin {
    @Shadow
    private Entity entity;

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

        MaidDamageProcessor.MaidHealthChange healthChange = MaidDamageProcessor.processMaidHealthDecrease(maid, requestedHealth);
        ci.cancel();
        if (healthChange.writeHealth()) {
            MaidHealthWriteGuard.runBypassing(() -> maid.getEntityData().set(maidspell$healthAccessor(), healthChange.health(), force));
        }
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
