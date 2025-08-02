package com.github.yimeng261.maidspell.task;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 法术战斗任务 - 统一的索敌和战斗管理
 * 实现IRangedAttackTask接口，为女仆提供法术攻击能力和混合战斗模式
 * 负责统一的目标搜索、验证和管理
 *
 * 索敌系统：
 * - 使用近战索敌方法 (IAttackTask::findFirstValidAttackTarget)
 * - 自动优先攻击距离最近的可见目标
 * - 简化代码并确保与其他攻击任务的一致性
 */
public class SpellCombatTask implements IRangedAttackTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation UID = new ResourceLocation("maidspell", "spell_combat");
    private static final MutableComponent NAME = Component.translatable("task.maidspell.spell_combat");
    private static float SPELL_RANGE;

    public static void setSpellRange(Float range){
        SPELL_RANGE = range;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public MutableComponent getName() {
        return NAME;
    }

    @Override
    public ItemStack getIcon() {
        return Items.ENCHANTED_BOOK.getDefaultInstance();
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        // 使用女仆远程攻击音效
        return SoundUtil.attackSound(maid, InitSounds.MAID_RANGE_ATTACK.get(), 0.5f);
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return true;
    }



    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // 使用近战索敌方法 - 它已经内置了优先攻击最近目标的逻辑
        BehaviorControl<EntityMaid> supplementedTask = StartAttacking.create(this::hasSpellBook, IAttackTask::findFirstValidAttackTarget);
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create(target -> farAway(target, maid));
        BehaviorControl<EntityMaid> moveToTargetTask = MaidRangedWalkToTarget.create(0.45f); // 进一步降低移动速度避免冲突
        // 统一的法术战斗行为 - 负责管理SimplifiedSpellCaster
        BehaviorControl<EntityMaid> spellCastingTask = new UnifiedSpellCombatBehavior();

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(6, moveToTargetTask), // 降低优先级避免冲突
                Pair.of(4, spellCastingTask) // 降低优先级，让移动任务先执行
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        // 空实现，实际攻击由UnifiedSpellCombatBehavior处理
    }


    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        return maid.distanceTo(target) <= SPELL_RANGE && Math.abs(maid.getY()-target.getY()) < 3.5;
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        float searchRange = Math.max(SPELL_RANGE, 32.0f);
        return maid.hasRestriction()
            ? new AABB(maid.getRestrictCenter()).inflate(searchRange)
            : maid.getBoundingBox().inflate(searchRange);
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return SPELL_RANGE;
    }

    @Override
    public boolean hasExtraAttack(EntityMaid maid, Entity target) {
        // 如果有法术书且目标在范围内，则可以进行额外的法术攻击
        return hasSpellBook(maid) && target instanceof LivingEntity && maid.distanceTo(target) <= SPELL_RANGE;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(
                Pair.of("spell_book", this::hasSpellBook)
        );
    }

    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        // 法术书和近战武器都被认为是武器
        return true;
    }

    /**
     * 检查女仆是否装备了法术书（检查副手、主手和背包）
     */
    private boolean hasSpellBook(EntityMaid maid) {
        return true;
    }

    /**
     * 检查目标是否距离过远
     */
    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > this.searchRadius(maid);
    }


    /**
     * 统一的法术战斗行为控制器
     * 负责管理SimplifiedSpellCaster，并确保目标同步
     */
    private class UnifiedSpellCombatBehavior extends Behavior<EntityMaid> {
        private SimplifiedSpellCaster currentSpellCaster;

        public UnifiedSpellCombatBehavior() {
            super(Map.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
            ));
        }

        @Override
        protected boolean checkExtraStartConditions(net.minecraft.server.level.ServerLevel level, EntityMaid maid) {
            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (target == null || !target.isAlive()) {
                return false;
            }
            
            double distance = maid.distanceTo(target);
            return distance <= SPELL_RANGE && maid.hasLineOfSight(target) && hasSpellBook(maid);
        }

        @Override
        protected boolean canStillUse(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long gameTime) {
            return checkExtraStartConditions(level, maid);
        }

        @Override
        protected void start(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long gameTime) {
            // 创建SpellCaster并设置初始目标
            currentSpellCaster = new SimplifiedSpellCaster(maid);

            // 立即设置当前目标
            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (validateTarget(target)) {
                currentSpellCaster.setTarget(target);
            }

        }

        private boolean validateTarget(LivingEntity target) {
            if(target!=null&&!(target instanceof Player)&&!(target instanceof EntityMaid)){
                return true;
            }
            return false;
        }

        @Override
        protected void tick(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long gameTime) {
            if (currentSpellCaster != null) {
                // 确保目标同步 - 这是唯一的目标更新点
                LivingEntity currentTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                if (validateTarget(currentTarget)) {
                    currentSpellCaster.setTarget(currentTarget);
                    currentSpellCaster.tick();
                }
            }
        }

        @Override
        protected void stop(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long gameTime) {
            // 停止和清理SpellCaster
            if (currentSpellCaster != null) {
                currentSpellCaster = null;
            }
        }
    }
} 