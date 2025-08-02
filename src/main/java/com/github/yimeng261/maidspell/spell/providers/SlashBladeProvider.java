package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData;

import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;

/**
 * SlashBlade模组的法术提供者
 * 为女仆提供拔刀剑SA使用能力
 */
public class SlashBladeProvider implements ISpellBookProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MaidSlashBladeData getData(EntityMaid maid) {
        return MaidSlashBladeData.getOrCreate(maid.getUUID());
    }

    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof ItemSlashBlade;
    }

    @Override
    public boolean castSpell(EntityMaid entityMaid) {
        return initiateCasting(entityMaid);
    }

    @Override
    public void updateCooldown(EntityMaid maid) {
        getData(maid).updateCooldowns();
    }

    @Override
    public void setTarget(EntityMaid maid, LivingEntity target) {
        MaidSlashBladeData data = getData(maid);
        data.setTarget(target);
        
        // 设置拔刀剑的目标实体ID
        ItemStack slashBlade = maid.getMainHandItem();
        if (isSpellBook(slashBlade)) {
            slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                if (target != null && target.isAlive()) {
                    state.setTargetEntityId(target.getId());
                }
            });
        }
    }

    @Override
    public LivingEntity getTarget(EntityMaid maid) {
        return getData(maid).getTarget();
    }

    @Override
    public void setSpellBook(EntityMaid maid, ItemStack slashBlade) {
        getData(maid).setSlashBlade(slashBlade);
    }

    @Override
    public boolean isCasting(EntityMaid maid) {
        return getData(maid).isCasting();
    }

    @Override
    public boolean initiateCasting(EntityMaid maid) {
        ItemStack itemStack = maid.getMainHandItem();
        if(!isSpellBook(itemStack)){
            return false;
        }
        
        MaidSlashBladeData data = getData(maid);
        if(maid.isUsingItem()){
            return false;
        }
        LivingEntity target = data.getTarget();

        if(data.isOnCooldown()||!hasSlashArt(itemStack)){
            if(maid.distanceTo(target)< SimplifiedSpellCaster.MELEE_RANGE){
                performSlashBladeAttack(maid, itemStack);
            }else{
                maid.getNavigation().moveTo(target,0.6);
            }
            return false;
        }

        // 检查拔刀剑状态
        return itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> {
            if (state.isBroken() || state.isSealed()) {
                return false;
            }

            // 为女仆添加INPUT_STATE能力（如果没有的话）
            ensureInputStateCapability(maid);
            
            // 开始蓄力
            maid.startUsingItem(InteractionHand.MAIN_HAND);
            
            // 设置数据状态
            data.setCasting(true);
            data.setSAExecutionStartTime(maid.level().getGameTime());
            data.setTargetUseTime(state.getFullChargeTicks(maid) + 5); // 稍微延长以确保充分蓄力
            
            return true;
        }).orElse(false);
    }

    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidSlashBladeData data = getData(maid);
        ItemStack slashBlade = maid.getMainHandItem();
        if (!isSpellBook(slashBlade)) {
            return;
        }

        if(data.isOnCooldown()||!data.isCasting()){
            return;
        }

        // 检查是否还在蓄力阶段
        if (maid.isUsingItem()) {
            // 蓄力阶段
            int ticksUsing = maid.getTicksUsingItem();
            if (ticksUsing >= data.getTargetUseTime()) {
                // 触发SA释放
                triggerSlashArt(maid, slashBlade);
            } else {
                // 持续蓄力过程中的处理
                slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                    // 确保女仆面向目标
                    LivingEntity target = data.getTarget();
                    if (target != null && target.isAlive()) {
                        // 修复：使用目标的眼部位置而不是脚部位置，确保刀气射向目标中心
                        Vec3 targetEyePos = target.getEyePosition();
                        maid.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetEyePos);
                        
                        // 额外的精确朝向调整，确保女仆的getLookAngle()返回正确的方向
                        forceLookAtTarget(maid, target);
                    }
                    
                    // 调用inventoryTick以保持拔刀剑状态更新
                    slashBlade.getItem().inventoryTick(slashBlade, maid.level(), maid, 0, true);
                });
            }
        } else {
            processComboExecution(maid, slashBlade);
        }
    }

    @Override
    public void stopCasting(EntityMaid maid) {
        MaidSlashBladeData data = getData(maid);
        
        if (maid.isUsingItem()) {
            maid.releaseUsingItem();
        }
        
        // 重置拔刀剑状态
        ItemStack slashBlade = maid.getMainHandItem();
        if (isSpellBook(slashBlade)) {
            slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                state.setLastActionTime(maid.level().getGameTime());
            });
        }
        
        data.reset();
        if(data.getCooldown()<-40){
            data.setCooldown(100);
        }


    }

    /**
     * 触发SA释放
     */
    private void triggerSlashArt(EntityMaid maid, ItemStack slashBlade) {
        int ticksUsing = maid.getTicksUsingItem();
        int useDuration = slashBlade.getUseDuration();
        int timeLeft = Math.max(0, useDuration - ticksUsing);

        slashBlade.releaseUsing(maid.level(), maid, timeLeft);
        maid.stopUsingItem();

         // 检查是否成功触发了combo
        slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            ResourceLocation currentCombo = state.getComboSeq();

            if (!currentCombo.equals(ComboStateRegistry.NONE.getId())) {
                // SA成功触发，开始combo执行阶段
                MaidSlashBladeData data = getData(maid);
                data.setSAExecutionStartTime(maid.level().getGameTime());
            } else {
                stopCasting(maid);
            }
        });
    }

    /**
     * 检查拔刀剑是否有SA
     * @param itemStack 拔刀剑物品
     * @return 是否有SA
     */
    public boolean hasSlashArt(ItemStack itemStack) {
        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof ItemSlashBlade)) {
            return false;
        }

        return itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> {
            return state.getSlashArtsKey() != null && !state.getSlashArtsKey().equals(SlashArtsRegistry.NONE.getId());
        }).orElse(false);
    }

    /**
     * 处理combo执行阶段（SA触发后）
     */
    private void processComboExecution(EntityMaid maid, ItemStack slashBlade) {
        MaidSlashBladeData data = getData(maid);
        
        // 检查是否超过最大执行时间（防止无限循环）
        long currentTime = maid.level().getGameTime();
        long executionTime = currentTime - data.getSAExecutionStartTime();
        if (executionTime > 200) { // 10秒超时（200 ticks = 10 seconds）
            LOGGER.debug("SA execution timeout, forcing stop");
            stopCasting(maid);
            return;
        }
        
        slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            ResourceLocation currentCombo = state.getComboSeq();

            if (currentCombo.equals(ComboStateRegistry.NONE.getId())) {
                stopCasting(maid);
                return;
            }
            
            // 在combo执行期间也要确保女仆朝向目标
            LivingEntity target = data.getTarget();
            if (target != null && target.isAlive()) {
                forceLookAtTarget(maid, target);
            }
            
            // 检查是否与上一次combo状态相同（防止状态停滞）
            ResourceLocation lastCombo = data.getLastComboState();
            if (lastCombo != null && lastCombo.equals(currentCombo)) {
                stopCasting(maid);
                return;
            }
            
            // 更新上一次combo状态
            data.setLastComboState(currentCombo);

            // 获取combo状态并执行tickAction
            ComboState comboState = ComboStateRegistry.REGISTRY.get().getValue(currentCombo);
            if (comboState != null) {
                slashBlade.getItem().inventoryTick(slashBlade, maid.level(), maid, 0, true);
            } else {
                stopCasting(maid);
            }
        });
    }

    /**
     * 确保女仆具有INPUT_STATE能力
     */
    private void ensureInputStateCapability(EntityMaid maid) {
        // 检查女仆是否已经有INPUT_STATE能力
        boolean hasInputState = maid.getCapability(ItemSlashBlade.INPUT_STATE).isPresent();
        
        if (hasInputState) {
            // 如果有能力，添加R_CLICK命令
            maid.getCapability(ItemSlashBlade.INPUT_STATE).ifPresent(inputState -> {
                inputState.getCommands().add(mods.flammpfeil.slashblade.util.InputCommand.R_CLICK);
            });
        }
    }

    /**
     * 执行拔刀剑普攻
     */
    private void performSlashBladeAttack(EntityMaid maid, ItemStack slashBlade) {
        // 使用拔刀剑的斩击机制
        int roll = maid.getRandom().nextInt(60) - 30;
        AttackManager.doSlash(maid, roll, Vec3.ZERO, false, false, 1.0, KnockBacks.smash);
        
        // 更新拔刀剑状态
        slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setLastActionTime(maid.level().getGameTime());
        });
        
        // 挥手动画
        maid.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * 强制女仆精确朝向目标
     */
    private void forceLookAtTarget(EntityMaid maid, LivingEntity target) {
        if (target == null || maid == null) return;
        
        double dx = target.getX() - maid.getX();
        double dy = target.getEyeY() - maid.getEyeY();
        double dz = target.getZ() - maid.getZ();
        
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        // 计算Yaw（水平旋转）
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        
        // 计算Pitch（垂直旋转）
        float pitch = (float) (-(Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI));
        
        // 设置女仆的朝向
        maid.setYRot(yaw);
        maid.setXRot(pitch);
        maid.yRotO = yaw;
        maid.xRotO = pitch;
        maid.yHeadRot = yaw;
        maid.yHeadRotO = yaw;
    }
}