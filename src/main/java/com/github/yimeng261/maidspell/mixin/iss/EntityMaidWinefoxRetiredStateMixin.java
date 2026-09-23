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
 * 把「战败坐下」这一位同步给客户端。
 *
 * <p>权威标记是女仆 ForgeData 里的 {@code MaidSpellWinefoxRetired}（见
 * {@link MagicalWinefoxBossEntity}）。它只随存档走：{@code ServerEntity.sendPairingData} 发的是
 * {@code Entity.getAddEntityPacket()} → {@code ClientboundAddEntityPacket}，那个包不带 NBT，
 * 于是客户端上 {@code isRetiredMaid} 永远是 false。
 *
 * <p>后果就是那个「只掉渲染、实体留下」的现象：玩家点魂符时，客户端照样把 TLM 的
 * {@code SlabClickEvent} 跑一遍（{@code MultiPlayerGameMode.interact} → {@code Player.interactOn}
 * 的本地预测），它 {@code maid.discard()} 掉的是客户端实体；而服务端因为读得到标记，
 * 早已把这次交互拒掉了（{@code PlayerInteractEvent.EntityInteract} 取消 +
 * {@code EntityMaidWinefoxRetiredInteractionMixin}）。两边一分叉，女仆就成了看不见的残留。
 *
 * <p>所以这里给 {@code EntityMaid} 加一个同步布尔，客户端也能读到同一份状态，
 * 交互在两边会被一起挡住。
 *
 * <p>accessor 的 id 必须在<b>第一次 {@code defineSynchedData} 时</b>才用
 * {@code SynchedEntityData.defineId} 申请：那时 {@code EntityMaid} 自己那 37 个 accessor 已经注册完，
 * 拿到的是下一个空闲编号。放进静态初始化就会抢在女仆之前拿到 0 号，和它撞号。
 *
 * <p><b>两个 {@code @Inject} 上的 {@code remap = true} 一个都不能删。</b>
 * 类上的 {@code remap = false} 是为了别去动 TLM 自己的成员名，但它同时会让注解处理器
 * 跳过这两个选择器的重映射 —— 而 {@code defineSynchedData} / {@code readAdditionalSaveData}
 * 都是原版方法在 TLM 里的覆写，正式包里叫 {@code m_8097_} / {@code m_7378_}。
 * 少了它，dev 下（方法名就是原名）一切正常，换成 jar 跑就当场
 * {@code InvalidInjectionException: could not find any targets matching 'defineSynchedData'}，
 * 而且是在 {@code EntityType} 初始化时炸，游戏根本进不去。
 * 同一个包里的 {@code EntityMaidWinefoxRetiredInteractionMixin} 就是照着这个写法来的。
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
