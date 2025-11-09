package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData;

import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SlashBlade模组的法术提供者
 * 为女仆提供拔刀剑SA使用能力
 * 使用直接技能系统，不依赖原模组状态机
 */
public class SlashBladeProvider extends ISpellBookProvider<MaidSlashBladeData, ResourceLocation> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM = new Random();
    
    /**
     * 直接技能枚举 - 使用函数式接口，完全自动化执行
     */
    public enum DirectSkill {
        // 基础斩击系列
        BASIC_SLASH("基础斩击", SkillType.BASIC, 0, maid -> {
            float roll = RANDOM.nextInt(60) - 30;
            AttackManager.doSlash(maid, roll, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        HORIZONTAL_SLASH("横斩", SkillType.BASIC, 0, maid -> {
            AttackManager.doSlash(maid, 0, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        VERTICAL_SLASH("纵斩", SkillType.BASIC, 0, maid -> {
            AttackManager.doSlash(maid, -90, Vec3.ZERO, false, false, 1.0, KnockBacks.cancel);
        }),
        
        // 连续攻击系列
        RAPID_SLASH("疾走居合", SkillType.COMBO, 1, maid -> {
            // 连续3次斩击
            for (int i = 0; i < 3; i++) {
                float roll = -45 + 90 * RANDOM.nextFloat() + (i * 180);
                AttackManager.doSlash(maid, roll, Vec3.ZERO, false, i == 2, 0.44, KnockBacks.cancel);
            }
        }),
        
        MULTI_SLASH("连续斩", SkillType.COMBO, 1, maid -> {
            // 连续5次快速斩击
            for (int i = 0; i < 5; i++) {
                float roll = -90 + 180 * RANDOM.nextFloat();
                Vec3 offset = new Vec3(
                    RANDOM.nextFloat() - 0.5f,
                    0,
                    RANDOM.nextFloat() - 0.5f
                );
                AttackManager.doSlash(maid, roll, offset, false, false, 0.24, KnockBacks.cancel);
            }
        }),
        
        // 特殊技能系列
        CIRCLE_SLASH("回旋斩", SkillType.SPECIAL, 2, maid -> {
            // 4个方向的斩击波，形成圆形
            for (int i = 0; i < 4; i++) {
                float yRot = i * 90;
                CircleSlash.doCircleSlashAttack(maid, yRot);
            }
        }),
        
        DRIVE_HORIZONTAL("驱动斩·横", SkillType.SPECIAL, 2, maid -> {
            Drive.doSlash(maid, 0F, 10, Vec3.ZERO, false, 1.5, 2f);
        }),
        
        DRIVE_VERTICAL("驱动斩·纵", SkillType.SPECIAL, 2, maid -> {
            Drive.doSlash(maid, -90F, 10, Vec3.ZERO, false, 1.5, 2f);
        }),
        
        JUDGEMENT_CUT("次元斩", SkillType.SPECIAL, 3, maid -> {
            JudgementCut.doJudgementCut(maid);
        }),
        
        // 空中技能系列
        AIR_SLASH("空中斩", SkillType.AERIAL, 1, maid -> {
            AttackManager.doSlash(maid, -20, Vec3.ZERO, false, false, 0.28, KnockBacks.cancel);
        }),
        
        AIR_SPIN("空中回旋", SkillType.AERIAL, 2, maid -> {
            // 空中旋转攻击
            for (int i = 0; i < 2; i++) {
                float roll = 180 + 57 + (i * 180);
                AttackManager.doSlash(maid, roll, Vec3.ZERO, false, false, 0.34, KnockBacks.toss);
            }
        });
        
        private final String displayName;
        private final SkillType type;
        private final int cooldownTicks;
        private final java.util.function.Consumer<EntityMaid> executor;
        
        DirectSkill(String displayName, SkillType type, int cooldownTicks, java.util.function.Consumer<EntityMaid> executor) {
            this.displayName = displayName;
            this.type = type;
            this.cooldownTicks = cooldownTicks;
            this.executor = executor;
        }
        
        public String getDisplayName() { return displayName; }
        public SkillType getType() { return type; }
        public int getCooldownTicks() { return cooldownTicks; }
        
        /**
         * 执行技能 - 完全自动化
         */
        public void execute(EntityMaid maid) {
            executor.accept(maid);
        }
    }
    
    /**
     * 技能类型
     */
    public enum SkillType {
        BASIC,      // 基础技能 - 60%权重
        COMBO,      // 连击技能 - 20%权重
        SPECIAL,    // 特殊技能 - 15%权重
        AERIAL      // 空中技能 - 5%权重
    }
    
    /**
     * SA释放类型枚举
     */
    public enum SAType {
        NORMAL,   // 普通Success版本 (15+ ticks)
        JUST,     // Just暴击版本 (9-14 ticks)
        SUPER     // Super超级版本 (20 ticks, 需要特殊条件)
    }
    
    /**
     * 地面基础技能池
     */
    private static final List<DirectSkill> GROUND_BASIC_SKILLS = List.of(
        DirectSkill.BASIC_SLASH,
        DirectSkill.HORIZONTAL_SLASH,
        DirectSkill.VERTICAL_SLASH
    );
    
    /**
     * 地面连击技能池
     */
    private static final List<DirectSkill> GROUND_COMBO_SKILLS = List.of(
        DirectSkill.RAPID_SLASH,
        DirectSkill.MULTI_SLASH
    );
    
    /**
     * 地面特殊技能池
     */
    private static final List<DirectSkill> GROUND_SPECIAL_SKILLS = List.of(
        DirectSkill.CIRCLE_SLASH,
        DirectSkill.DRIVE_HORIZONTAL,
        DirectSkill.DRIVE_VERTICAL,
        DirectSkill.JUDGEMENT_CUT
    );
    
    /**
     * 空中技能池
     */
    private static final List<DirectSkill> AERIAL_SKILLS = List.of(
        DirectSkill.AIR_SLASH,
        DirectSkill.AIR_SPIN
    );

    /**
     * 构造函数，绑定 MaidSlashBladeData 数据类型和 ResourceLocation 法术类型
     * 注：对于SlashBlade，SA（Slash Art）通过ResourceLocation标识
     */
    public SlashBladeProvider() {
        super(MaidSlashBladeData::getOrCreate, ResourceLocation.class);
    }

    /**
     * 从单个拔刀剑中收集SA
     * @param spellBook 拔刀剑物品堆栈
     * @return 该拔刀剑的SA列表（通常只有一个）
     */
    @Override
    protected List<ResourceLocation> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<ResourceLocation> slashArts = new ArrayList<>();
        
        if (spellBook == null || spellBook.isEmpty() || !isSpellBook(spellBook)) {
            return slashArts;
        }
        
        // 从拔刀剑的BladeState中获取SA
        spellBook.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            ResourceLocation slashArtKey = state.getSlashArtsKey();
            if (slashArtKey != null && !slashArtKey.equals(SlashArtsRegistry.NONE.getId())) {
                slashArts.add(slashArtKey);
            }
        });
        
        return slashArts;
    }
    
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof ItemSlashBlade;
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
    public void initiateCasting(EntityMaid maid) {
        LOGGER.debug("[MaidSpell] Initiate casting for slashblade");
        ItemStack itemStack = maid.getMainHandItem();

        MaidSlashBladeData data = getData(maid);
        LivingEntity target = data.getTarget();

        if(data.isOnCooldown()||!hasSlashArt(itemStack)){
            if(maid.distanceTo(target)< SimplifiedSpellCaster.MELEE_RANGE){
                performSlashBladeAttack(maid, itemStack);
            }else{
                maid.getNavigation().moveTo(target,0.6);
            }
            return;
        }

        LOGGER.debug("[MaidSpell] Initiate casting for slashblade - start charging");
        // 检查拔刀剑状态
        itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> {
            if (state.isBroken() || state.isSealed()) {
                return false;
            }

            // 开始蓄力
            maid.startUsingItem(InteractionHand.MAIN_HAND);

            // 随机选择SA释放类型（排除Fail）
            SAType saType = randomSAType();
            LOGGER.debug("[MaidSpell] Selected SA type: {}", saType);
            
            // 根据SA类型计算目标蓄力时间
            int targetUseTime = calculateTargetUseTime(saType, state, maid);
            LOGGER.debug("[MaidSpell] Target use time: {} ticks", targetUseTime);

            // 设置数据状态
            data.setCasting(true);
            data.setSAExecutionStartTime(maid.level().getGameTime());
            data.setTargetUseTime(targetUseTime);

            return true;
        });
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
                        BehaviorUtils.lookAtEntity(maid, target);
                        Vec3 targetEyePos = target.getEyePosition();
                        maid.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetEyePos);
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

        return itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> state.getSlashArtsKey() != null && !state.getSlashArtsKey().equals(SlashArtsRegistry.NONE.getId())).orElse(false);
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
                BehaviorUtils.lookAtEntity(maid, target);
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
     * 执行拔刀剑普攻 - 使用直接技能系统
     */
    private void performSlashBladeAttack(EntityMaid maid, ItemStack slashBlade) {
        LOGGER.debug("[MaidSpell] Performing slash blade attack with direct skill");
        
        // 选择随机技能
        DirectSkill skill = selectRandomSkill(maid);
        LOGGER.debug("[MaidSpell] Selected skill: {}", skill.getDisplayName());
        
        // 直接执行技能效果
        executeDirectSkill(maid, slashBlade, skill);
        
        // 挥手动画
        maid.swing(InteractionHand.MAIN_HAND);
    }
    
    /**
     * 直接执行技能效果 - 使用函数式接口，完全自动化
     */
    private void executeDirectSkill(EntityMaid maid, ItemStack slashBlade, DirectSkill skill) {
        // 确保女仆面向目标
        MaidSlashBladeData data = getData(maid);
        LivingEntity target = data.getTarget();
        if (target != null && target.isAlive()) {
            BehaviorUtils.lookAtEntity(maid, target);
        }
        
        // ⭐ 一行代码执行所有技能！
        skill.execute(maid);
        
        // 更新拔刀剑状态
        slashBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setLastActionTime(maid.level().getGameTime());
        });
    }
    
    /**
     * 随机选择一个技能
     * 根据女仆状态（地面/空中）和随机权重选择
     */
    private DirectSkill selectRandomSkill(EntityMaid maid) {
        List<DirectSkill> availableSkills;
        
        if (maid.onGround()) {
            // 地面时根据权重选择技能类型
            int roll = RANDOM.nextInt(100);
            
            if (roll < 60) {
                // 60% - 基础技能
                availableSkills = GROUND_BASIC_SKILLS;
            } else if (roll < 80) {
                // 20% - 连击技能
                availableSkills = GROUND_COMBO_SKILLS;
            } else {
                // 20% - 特殊技能
                availableSkills = GROUND_SPECIAL_SKILLS;
            }
        } else {
            // 空中时使用空中技能
            availableSkills = AERIAL_SKILLS;
        }
        
        // 从选中的池中随机选择一个技能
        return availableSkills.get(RANDOM.nextInt(availableSkills.size()));
    }
    
    /**
     * 随机选择一个SA类型（排除Fail）
     * 权重分配：NORMAL=50%, JUST=40%, SUPER=10%
     */
    private SAType randomSAType() {
        int roll = RANDOM.nextInt(100);
        
        if (roll < 10) {
            return SAType.SUPER;   // 10% 几率使用Super
        } else if (roll < 50) {
            return SAType.JUST;    // 40% 几率使用Just
        } else {
            return SAType.NORMAL;  // 50% 几率使用Normal
        }
    }
    
    /**
     * 计算目标蓄力时间（SA使用）
     */
    private int calculateTargetUseTime(SAType saType, mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state, EntityMaid maid) {
        int fullChargeTicks = state.getFullChargeTicks(maid);  // 默认9 ticks
        int justSpan = SlashArts.getJustReceptionSpan(maid);   // 默认3-5 ticks
        
        switch (saType) {
            case JUST:
                // Just窗口期：fullChargeTicks ~ (fullChargeTicks + justSpan)
                // 取窗口期的中间偏前位置，确保稳定触发Just
                return fullChargeTicks + (justSpan / 2);  // 例如: 9 + 2 = 11 ticks
                
            case SUPER:
                // Super需要长按约20 ticks
                return 20;
                
            case NORMAL:
            default:
                // Normal成功版本: 超过Just窗口期
                return fullChargeTicks + justSpan + 2;  // 例如: 9 + 5 + 2 = 16 ticks
        }
    }

}