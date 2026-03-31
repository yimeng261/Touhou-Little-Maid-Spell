package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import javax.annotation.Nullable;

/**
 * EntityMaid的Mixin，用于:
 * 1. 替换女仆背包处理器为支持法术书变化监听的版本
 * 2. 替换女仆饰品处理器为支持女仆实体关联的版本
 * 3. 修改finalizeSpawn方法，使hidden_retreat结构中的女仆structureSpawn不为true
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
     * 修改finalizeSpawn方法，阻止hidden_retreat结构中的女仆进行随机模型选择
     * 在方法开头注入，如果检测到是在hidden_retreat结构中生成，则提前返回
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
     * 拦截直接对女仆附加存档数据的调用，防止第三方通过 addAdditionalSaveData 复制女仆状态。
     */
    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onAddAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        try {
            if ((Object) this instanceof EntityMaid maid) {
                if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                    return;
                }

                String illegalCaller = maidSpell$findIllegalSerializationCaller();
                if (illegalCaller != null) {
                    maidSpell$clearCompound(compound);
                    Global.LOGGER.warn("[MaidSpell] Illegal addAdditionalSaveData called for {} by {} (anchor_core protection)",
                            maid.getUUID(), illegalCaller);
                    ci.cancel();
                }
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
                if (!maidSpell$isCallValid(reason)) {
                    Global.LOGGER.debug("Prevented non-TLM removal of maid {} with health {} (anchor_core protection)",
                            maid.getUUID(), maid.getHealth());
                    ci.cancel();
                }
            }

        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid removal source", e);
        }
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

    /**
     * 检查 addAdditionalSaveData 的调用栈，找到第一个不可信的调用方。
     */
    @Unique
    @Nullable
    private static String maidSpell$findIllegalSerializationCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            String className = stackTraceElement.getClassName();
            if (className.endsWith("EntityMaid")) {
                continue;
            }
            if (!maidSpell$isSerializationCallerAllowed(className)) {
                return className;
            }
        }
        return null;
    }

    @Unique
    private static void maidSpell$clearCompound(CompoundTag compound) {
        for (String key : new java.util.ArrayList<>(compound.getAllKeys())) {
            compound.remove(key);
        }
    }

    /**
     * addAdditionalSaveData 允许的调用方白名单。
     */
    @Unique
    private static boolean maidSpell$isSerializationCallerAllowed(String className) {
        return className.startsWith("net.minecraft") ||
                className.startsWith("net.minecraftforge") ||
                className.startsWith("java") ||
                className.startsWith("it.unimi.dsi") ||
                className.startsWith("com.github.tartaricacid") ||
                className.startsWith("com.github.yimeng261") ||
                className.startsWith("com.google") ||
                className.startsWith("com.mojang") ||
                className.startsWith("io.redspace.ironsspellbooks") ||
                className.startsWith("whocraft.tardis_refined") ||
                className.startsWith("top.theillusivec4.curios") ||
                className.contains("backup") ||
                className.contains("maid") ||
                className.contains("c2me");
    }

    /**
     * 检查调用栈是否来自touhou-little-maid模组或其他可信任的模组
     * @return 如果调用来自可信任模组返回true
     */
    @Unique
    private boolean maidSpell$isCallValid(Entity.RemovalReason reason) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean callFromTouhouLittleMaidMod = false;
        for(int i=stackTrace.length-10; i>=0; i--) {
            StackTraceElement stackTraceElement = stackTrace[i];
            String className = stackTraceElement.getClassName();
            //Global.LOGGER.debug("className {}", className);
            if(className.endsWith("EntityMaid")) {
                continue;
            }
            if (className.toLowerCase().contains("tlm") || 
                className.toLowerCase().contains("maid") ||
                className.contains("ironsspellbooks") ||
                className.contains("curios")) {
                callFromTouhouLittleMaidMod = true;
                break;
            }
            if (reason == Entity.RemovalReason.CHANGED_DIMENSION
                    && className.startsWith("whocraft.tardis_refined")) {
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
        @SuppressWarnings("removal")
        var hiddenRetreatStructureSet = worldIn.registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
            .getOptional(new ResourceLocation("touhou_little_maid_spell", "hidden_retreat"));

        if (hiddenRetreatStructureSet.isPresent()) {
            // 检查此位置是否在hidden_retreat结构的范围内
            var structureStart = structureManager.getStructureWithPieceAt(pos, hiddenRetreatStructureSet.get());
            return structureStart.isValid();
        }
        return false;
    }
} 
