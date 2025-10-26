package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.inventory.MaidAwareBaubleItemHandler;
import com.github.yimeng261.maidspell.inventory.SpellBookAwareMaidBackpackHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
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
public abstract class EntityMaidMixin extends TamableAnimal {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    private boolean structureSpawn;

    @Mutable
    @Final
    @Shadow
    private ItemStackHandler maidInv;

    @Mutable
    @Final
    @Shadow
    private BaubleItemHandler maidBauble;

    protected EntityMaidMixin(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * 在构造函数完成后替换字段值
     */
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
            at = @At("TAIL"))
    private void replaceHandlers(EntityType<EntityMaid> type, Level world, CallbackInfo ci) {
        // 使用Shadow字段直接替换背包处理器
        this.maidInv = new SpellBookAwareMaidBackpackHandler(36, (EntityMaid)(Object)this);

        // 使用Shadow字段直接替换饰品处理器
        this.maidBauble = new MaidAwareBaubleItemHandler(9, (EntityMaid)(Object)this);
    }

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
            .getOptional(new ResourceLocation("touhou_little_maid_spell", "hidden_retreat"));

        if (hiddenRetreatStructureSet.isPresent()) {
            // 检查此位置是否在hidden_retreat结构的范围内
            var structureStart = structureManager.getStructureWithPieceAt(pos, hiddenRetreatStructureSet.get());
            return structureStart.isValid();
        }
        return false;
    }
}
