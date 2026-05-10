package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityMaid的Mixin，用于:
 * 1. 暴露女仆背包/饰品处理器的 Shadow 访问
 * 2. 维护 maidspell 锚定状态的 SynchedEntityData
 * 3. 实现 AnchoredEntityMaid 接口
 *
 * 其他职责（锚核保护、狐叶移动、GUI 走位、结构生成）已拆分到独立 mixin。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidMixin extends TamableAnimal implements AnchoredEntityMaid {
    @Unique
    private static final EntityDataAccessor<Boolean> MAID_SPELL_DATA_ANCHORED =
            SynchedEntityData.defineId(EntityMaidMixin.class, EntityDataSerializers.BOOLEAN);

    protected EntityMaidMixin(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract BaubleItemHandler getMaidBauble();

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    protected void afterDefineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(MAID_SPELL_DATA_ANCHORED, false);
    }

    @Override
    public boolean maidSpell$isAnchored() {
        return entityData.get(MAID_SPELL_DATA_ANCHORED);
    }

    @Override
    public void maidSpell$setAnchored(boolean anchored) {
        entityData.set(MAID_SPELL_DATA_ANCHORED, anchored);
    }
}
