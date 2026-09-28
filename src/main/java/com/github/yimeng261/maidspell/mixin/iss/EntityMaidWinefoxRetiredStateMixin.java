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
 * accessor ID 必须在类初始化时申请：Builder 构造时按 ID 池大小分配数组，晚于此申请的 ID 会越界。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidWinefoxRetiredStateMixin implements WinefoxRetiredStateAccessor {
    @Unique
    private static final EntityDataAccessor<Boolean> MAIDSPELL$WINEFOX_RETIRED =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V", at = @At("TAIL"))
    private void maidspell$defineWinefoxRetired(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(MAIDSPELL$WINEFOX_RETIRED, false);
    }

    /**
     * 读档时把存档里的持久数据标记补进同步位。
     *
     * <p>{@code Entity.load} 先读 NeoForgeData 再调 {@code readAdditionalSaveData}，所以这里能读到标记；
     * 否则重登之后到酒狐下一次 tick 之前，客户端仍以为她没退场，魂符交互会漏过去。
     */
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void maidspell$restoreWinefoxRetired(CompoundTag tag, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (MagicalWinefoxBossEntity.isRetiredMaid(maid)) {
            ((WinefoxRetiredStateAccessor) maid).maidspell$setWinefoxRetired(true);
        }
    }

    @Override
    public boolean maidspell$isWinefoxRetired() {
        return ((EntityMaid) (Object) this).getEntityData().get(MAIDSPELL$WINEFOX_RETIRED);
    }

    @Override
    public void maidspell$setWinefoxRetired(boolean retired) {
        ((EntityMaid) (Object) this).getEntityData().set(MAIDSPELL$WINEFOX_RETIRED, retired);
    }
}
