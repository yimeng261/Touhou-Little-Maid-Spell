package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.inventory.MaidAwareBaubleItemHandler;
import com.github.yimeng261.maidspell.inventory.SpellBookAwareMaidBackpackHandler;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
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
import oshi.util.tuples.Pair;

import java.util.function.Function;

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
        LogUtils.getLogger().info("[MaidSpell] Replacing maid handlers in constructor");

        // 使用Shadow字段直接替换背包处理器
        this.maidInv = new SpellBookAwareMaidBackpackHandler(36, (EntityMaid)(Object)this);
        LogUtils.getLogger().info("[MaidSpell] Successfully replaced maidInv with SpellBookAwareMaidBackpackHandler");

        // 使用Shadow字段直接替换饰品处理器
        this.maidBauble = new MaidAwareBaubleItemHandler(9, (EntityMaid)(Object)this);
        LogUtils.getLogger().info("[MaidSpell] Successfully replaced maidBauble with MaidAwareBaubleItemHandler");

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

                if (isInHiddenRetreatStructure(worldIn, maidPos)) {
                    this.structureSpawn = false;
                    LogUtils.getLogger().info("Prevented finalizeSpawn processing for maid in hidden_retreat structure at {}", maidPos);
                    cir.setReturnValue(spawnDataIn);
                }
            }
        } catch (Exception e) {
            LogUtils.getLogger().error("Failed to prevent finalizeSpawn processing for hidden_retreat maid", e);
        }
    }


    /**
     * 检查指定位置是否在hidden_retreat结构中
     * @param worldIn 世界访问器
     * @param pos 检查的位置
     * @return 如果在hidden_retreat结构中返回true
     */
    private boolean isInHiddenRetreatStructure(ServerLevelAccessor worldIn, BlockPos pos) {
        try {
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
        } catch (Exception e) {
            LogUtils.getLogger().debug("Error checking hidden_retreat structure at {}: {}", pos, e.getMessage());
        }
        return false;
    }

    @Override
    public void setHealth(float health) {
        try {
            float currentHealth = getHealth();
            //治疗则不处理
            if (health >= currentHealth) {
                super.setHealth(health);
                return;
            }

            EntityMaid maid = (EntityMaid) (Object) this;
            LivingEntityAccessor accessor = (LivingEntityAccessor) maid;

            DataItem dataItem = new DataItem(maid, currentHealth - health);

            SliverCercisBauble_process(dataItem); //优先处理银链

            SoulBookBauble_process(dataItem); //优先处理魂之书

            Global.bauble_hurtProcessors_final.forEach((key, func) -> func.apply(dataItem));

            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                Function<DataItem, Void> func = Global.bauble_hurtProcessors_pre.getOrDefault(bauble.getDescriptionId(), (d) -> null);
                func.apply(dataItem);
            });

            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                Function<DataItem, Void> func = Global.bauble_hurtProcessors_final.getOrDefault(bauble.getDescriptionId(), (d) -> null);
                func.apply(dataItem);
            });

            if(dataItem.isCanceled()){
                dataItem.setAmount(0);
            }

            float finalHealth = Math.max(0.0f, currentHealth - dataItem.getAmount());
            getEntityData().set(accessor.getDataHealthIdAccessor(), finalHealth);

            return;

        } catch (Exception e) {
            LOGGER.error("[SoulBookBauble] Error processing setHealth modification for maid {}", getUUID(), e);
        }
        super.setHealth(health);
    }

    @Unique
    private void SoulBookBauble_process(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if(!BaubleStateManager.hasBauble(maid, MaidSpellItems.SOUL_BOOK)){
            return;
        }
        float damage = dataItem.getAmount();
        Pair<Boolean, Float> result = SoulBookBauble.damageCalc(maid, damage);
        if (!result.getA()) {
            LOGGER.debug("[SoulBookBauble] Damage cancelled for maid {} due to insufficient interval", maid.getUUID());
            dataItem.setCanceled(true);
            return;
        }
        dataItem.setAmount(result.getB());
        SoulBookBauble.lastHurtTimeMap.put(maid.getUUID(), maid.tickCount);
    }

    @Unique
    private void SliverCercisBauble_process(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if(!BaubleStateManager.hasBauble(maid, MaidSpellItems.SLIVER_CERCIS)){
            return;
        }
        LivingEntity target = maid.getLastAttacker();
        if(!target.isAlive()){
            target = maid.getTarget();
        }
        TrueDamageUtil.dealTrueDamage(target, dataItem.getAmount()*0.8f);
    }
}
