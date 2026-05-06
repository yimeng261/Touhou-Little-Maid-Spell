package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.mixin.accessor.JumpControlAccessor;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.utils.FoxLeafSurfaceHelper;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import com.github.yimeng261.maidspell.utils.MaidMovementHelper;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;


import javax.annotation.Nullable;

/**
 * EntityMaid的Mixin，用于:
 * 1. 替换女仆背包处理器为支持法术书变化监听的版本
 * 2. 替换女仆饰品处理器为支持女仆实体关联的版本
 * 3. 修改finalizeSpawn方法，使特定结构中的女仆跳过随机模型选择
 */
@Mixin(value = EntityMaid.class,remap = false)
public class EntityMaidMixin {
    
    /**
     * Shadow字段，用于直接访问EntityMaid的私有字段
     */
    @Shadow
    private boolean structureSpawn;

    @Shadow
    public boolean guiOpening;

    
    /**
     * 修改finalizeSpawn方法，阻止指定结构中的女仆进行随机模型选择。
     * 在方法开头注入，如果检测到是在目标结构中生成，则提前返回。
     */
    @Inject(method = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;", 
            at = @At("HEAD"), 
            cancellable = true, remap = true)
    public void onFinalizeSpawn(ServerLevelAccessor worldIn, DifficultyInstance difficultyIn, MobSpawnType reason,
                                @Nullable SpawnGroupData spawnDataIn, @Nullable CompoundTag dataTag,
                                CallbackInfoReturnable<SpawnGroupData> cir) {
        try {
            // 只在结构生成时检查
            if (reason == MobSpawnType.STRUCTURE) {
                EntityMaid maid = (EntityMaid)(Object)this;
                BlockPos maidPos = maid.blockPosition();

                if (maidSpell$shouldSkipRandomModel(worldIn, maidPos)) {
                    this.structureSpawn = false;
                    Global.LOGGER.debug("Prevented finalizeSpawn processing for maid in protected structure at {}", maidPos);
                    cir.setReturnValue(spawnDataIn);
                }
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to prevent finalizeSpawn processing for protected-structure maid", e);
        }
    }


    /**
     * 拦截直接对女仆附加存档数据的调用，防止第三方通过 addAdditionalSaveData 复制女仆状态。
     */
    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onAddAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        try {
            EntityMaid maid = (EntityMaid)(Object)this;
            if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                return;
            }
            String illegalCaller = AnchorCoreBauble.findIllegalCaller();
            if (illegalCaller != null) {
                AnchorCoreBauble.clearCompound(compound);
                Global.LOGGER.warn("[MaidSpell] Illegal addAdditionalSaveData called for {} by {} (anchor_core protection)",
                        maid.getUUID(), illegalCaller);
                ci.cancel();
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid addAdditionalSaveData source", e);
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
            EntityMaid maid = (EntityMaid)(Object)this;
            if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                Global.LOGGER.debug("Maid {} does not have anchor_core, allowing removal", maid.getUUID());
                return;
            }
            Global.LOGGER.debug("remove called for {}", maid);
            if (maid.getHealth() <= 0.0f) {
                return;
            }
            if (!maidSpell$isCallValid(reason)) {
                Global.LOGGER.debug("Prevented non-TLM removal of maid {} with health {} (anchor_core protection)",
                        maid.getUUID(), maid.getHealth());
                ci.cancel();
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid removal source", e);
        }
    }

    /**
     * TLM's remove(KILLED) path can call dropEquipment after a bauble already
     * revived the maid. In that state the entity is alive, so no tombstone/film
     * should be generated or inventory extracted.
     */
    @Inject(method = "dropEquipment()V",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    private void maidspell$preventAliveAnchoredMaidTombstone(CallbackInfo ci) {
        try {
            EntityMaid maid = (EntityMaid) (Object) this;
            if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                return;
            }

            if (maid.getHealth() > 0.0f) {
                Global.LOGGER.debug("Prevented alive maid {} from generating tombstone with health {} (anchor_core protection)",
                        maid.getUUID(), maid.getHealth());
                ci.cancel();
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid tombstone generation", e);
        }
    }

    @Inject(method = "aiStep()V", at = @At("HEAD"), remap = true)
    private void maidspell$clearResidualMovementWhenGuiOpen(CallbackInfo ci) {
        if (!guiOpening) {
            return;
        }

        MaidMovementHelper.stopAllMovement((EntityMaid) (Object) this);
    }

    @Redirect(
        method = "aiStep()V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/MaidNavigationManager;tick()V",
            remap = false
        ),
        remap = true
    )
    private void maidspell$skipNavigationManagerTickWhileGuiOpening(
        com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidNavigationManager navigationManager
    ) {
        if (!guiOpening) {
            navigationManager.tick();
        }
    }

    @Inject(method = "canBrainMoving()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$blockBrainMovementChecksWhileGuiOpening(CallbackInfoReturnable<Boolean> cir) {
        if (guiOpening) {
            cir.setReturnValue(false);
        }
    }

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

    @Inject(method = "customServerAiStep()V", at = @At("TAIL"), remap = true)
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

    @Unique
    private boolean maidSpell$isCallValid(Entity.RemovalReason reason) {
        if (MaidHardRemovalProtection.isForcingProtectionCheck()) {
            return false;
        }
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = stackTrace.length - 10; i >= 0; i--) {
            String className = stackTrace[i].getClassName();
            if (className.endsWith("EntityMaid")) continue;
            if (AnchorCoreBauble.isCallerAllowed(className)) return true;
            if (reason == Entity.RemovalReason.CHANGED_DIMENSION
                    && className.startsWith("whocraft.tardis_refined")) return true;
        }
        return false;
    }

    /**
     * 检查指定位置是否在需要阻断随机模型的结构中
     * @param worldIn 世界访问器
     * @param pos 检查的位置
     * @return 如果在目标结构中返回true
     */
    private static final List<String> PROTECTED_STRUCTURES =
            List.of("hidden_retreat", "relic_sanctum", "fallen_sanctum", "elven_realm");

    @Unique
    private boolean maidSpell$shouldSkipRandomModel(ServerLevelAccessor worldIn, BlockPos pos) {
        StructureManager structureManager = worldIn.getLevel().structureManager();
        Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
                worldIn.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return PROTECTED_STRUCTURES.stream().anyMatch(id ->
                maidSpell$isInsideStructure(structureManager, structureRegistry, pos, id));
    }

    @Unique
    private static boolean maidSpell$isInsideStructure(StructureManager structureManager,
                                                       Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry,
                                                       BlockPos pos,
                                                       String structureId) {
        @SuppressWarnings("removal")
        var structure = structureRegistry.getOptional(new ResourceLocation("touhou_little_maid_spell", structureId));
        return structure.isPresent() && structureManager.getStructureWithPieceAt(pos, structure.get()).isValid();
    }
} 
