package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxRetiredStateAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将战败坐姿标记同步给客户端，防止本地预测仍允许魂符交互。
 * accessor ID 在 defineSynchedData 中申请，避免与 TLM 字段撞号。
 * 两处 Inject 均须 remap=true，因为目标是原版方法在 TLM 中的覆写。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidWinefoxRetiredStateMixin implements WinefoxRetiredStateAccessor {
    @Unique
    private static EntityDataAccessor<Boolean> maidspell$winefoxRetired;

    @Unique
    private static EntityDataAccessor<Boolean> maidspell$winefoxRetiredAccessor() {
        if (maidspell$winefoxRetired == null) {
            maidspell$winefoxRetired = SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
        }
        return maidspell$winefoxRetired;
    }

    @Inject(method = "defineSynchedData()V", at = @At("TAIL"), remap = true)
    private void maidspell$defineWinefoxRetired(CallbackInfo ci) {
        ((EntityMaid) (Object) this).getEntityData().define(maidspell$winefoxRetiredAccessor(), false);
    }

    /**
     * 读档时把存档里的 ForgeData 标记补进同步位。
     *
     * <p>{@code Entity.load} 先读 ForgeData（第 1876 行）再调 {@code readAdditionalSaveData}（第 1889 行），
     * 所以这里能读到标记。少了这一句，重登之后到酒狐下一次 tick 之间客户端手里还是 false，
     * 那一小段窗口里魂符又会漏过去。
     */
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = true)
    private void maidspell$restoreWinefoxRetired(CompoundTag tag, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (MagicalWinefoxBossEntity.isRetiredMaid(maid)) {
            ((WinefoxRetiredStateAccessor) maid).maidspell$setWinefoxRetired(true);
        }
    }

    @Override
    public boolean maidspell$isWinefoxRetired() {
        return ((EntityMaid) (Object) this).getEntityData().get(maidspell$winefoxRetiredAccessor());
    }

    @Override
    public void maidspell$setWinefoxRetired(boolean retired) {
        ((EntityMaid) (Object) this).getEntityData().set(maidspell$winefoxRetiredAccessor(), retired);
    }
}
