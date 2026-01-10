package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityMaid的Mixin，用于:
 * 1. 替换女仆背包处理器为支持法术书变化监听的版本
 * 2. 替换女仆饰品处理器为支持女仆实体关联的版本
 * 3. 修改finalizeSpawn方法，使hidden_retreat结构中的女仆structureSpawn不为true
 */
@Mixin(value = EntityMaid.class,remap = false)
public abstract class EntityMaidMixin extends TamableAnimal implements AnchoredEntityMaid {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private static final EntityDataAccessor<Boolean> MAID_SPELL_DATA_ANCHORED = SynchedEntityData.defineId(EntityMaidMixin.class, EntityDataSerializers.BOOLEAN);

    /**
     * Shadow字段，用于直接访问EntityMaid的私有字段
     */
    @Shadow
    private boolean structureSpawn;

    @Shadow
    public boolean guiOpening;


    protected EntityMaidMixin(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }


    @Shadow public abstract BaubleItemHandler getMaidBauble();

    /**
     * 修改finalizeSpawn方法，阻止hidden_retreat结构中的女仆进行随机模型选择
     * 在方法开头注入，如果检测到是在hidden_retreat结构中生成，则提前返回
     */
    @Inject(method = "finalizeSpawn",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onFinalizeSpawn(ServerLevelAccessor worldIn, DifficultyInstance difficultyIn, MobSpawnType reason, SpawnGroupData spawnDataIn, CallbackInfoReturnable<SpawnGroupData> cir) {
        try {
            // 只在结构生成时检查
            if (reason == MobSpawnType.STRUCTURE) {
                EntityMaid maid = (EntityMaid)(Object)this;
                BlockPos maidPos = maid.blockPosition();

                if (maidSpell$isInHiddenRetreatStructure(worldIn, maidPos)) {
                    this.structureSpawn = false;
                    Global.LOGGER.debug("Prevented finalizeSpawn processing for maid in hidden_retreat structure at {}", maidPos);
                    cir.setReturnValue(spawnDataIn);
                }
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to prevent finalizeSpawn processing for hidden_retreat maid", e);
        }
    }


    /**
     * 拦截女仆的remove方法，防止非正常途径移除血量不为0的女仆
     */
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        try {
            if((Object)this instanceof EntityMaid maid) {

                // 检查女仆是否装备了锚定核心饰品
                if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                    Global.LOGGER.debug("Maid {} does not have anchor_core, allowing removal", maid.getUUID());
                    return;
                }

                // 如果女仆血量为0，允许正常移除
                Global.LOGGER.debug("remove called for {}", maid);
                if (maid.getHealth() <= 0.0f) {
                    return;
                }

                // 检查调用栈，判断是否来自touhou-little-maid模组
                if (!maidSpell$isCallValid()) {
                    Global.LOGGER.debug("Prevented non-TLM removal of maid {} with health {} (anchor_core protection)",
                            maid.getUUID(), maid.getHealth());
                    ci.cancel();
                }
            }

        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid removal source", e);
        }
    }

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    protected void afterCustomServerAiStep(CallbackInfo ci) {
        if (guiOpening) {
            // 打开 GUI 时停止施法和走位
            EntityMaid maid = (EntityMaid)(Object)this;
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.stopAllCasting();
            maid.getNavigation().stop();
            maid.getMoveControl().strafe(0, 0);
        }
    }

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

    /**
     * 检查调用栈是否来自touhou-little-maid模组
     * @return 如果调用来自TLM模组返回true
     */
    @Unique
    private boolean maidSpell$isCallValid() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean callFromTouhouLittleMaidMod = false;
        for(int i=stackTrace.length-10; i>=0; i--) {
            StackTraceElement stackTraceElement = stackTrace[i];
            String className = stackTraceElement.getClassName();
            //Global.LOGGER.debug("className {}", className);
            if(className.endsWith("EntityMaid")) {
                continue;
            }
            if (className.toLowerCase().contains("tlm") || className.toLowerCase().contains("maid")) {
                callFromTouhouLittleMaidMod = true;
                break;
            }
        }

        return callFromTouhouLittleMaidMod;
    }

    /**
     * 检查指定位置是否在hidden_retreat结构中
     * @param worldIn 世界访问器
     * @param pos 检查的位置
     * @return 如果在hidden_retreat结构中返回true
     */
    @Unique
    private boolean maidSpell$isInHiddenRetreatStructure(ServerLevelAccessor worldIn, BlockPos pos) {
        // 检查当前位置是否在hidden_retreat结构中
        // 使用结构管理器检查
        var structureManager = worldIn.getLevel().structureManager();
        var hiddenRetreatStructureSet = worldIn.registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
            .getOptional(new ResourceLocation(MaidSpellMod.MOD_ID, "hidden_retreat"));

        if (hiddenRetreatStructureSet.isPresent()) {
            // 检查此位置是否在hidden_retreat结构的范围内
            var structureStart = structureManager.getStructureWithPieceAt(pos, hiddenRetreatStructureSet.get());
            return structureStart.isValid();
        }
        return false;
    }
}
