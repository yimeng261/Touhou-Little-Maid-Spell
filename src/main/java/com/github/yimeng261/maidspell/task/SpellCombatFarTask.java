package com.github.yimeng261.maidspell.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;


import com.github.yimeng261.maidspell.spell.manager.AllianceManager;

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
public class SpellCombatFarTask extends SpellCombatMeleeTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation UID = new ResourceLocation("maidspell", "spell_combat_far");
    private static final MutableComponent NAME = Component.translatable("task.maidspell.spell_combat_far");
    private static float SPELL_RANGE;

    public static void setSpellRange(Float range){
        SPELL_RANGE = range;
    }

    @Override
    public @NotNull ResourceLocation getUid() {
        return UID;
    }

    @Override
    public @NotNull MutableComponent getName() {
        return NAME;
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }


    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> supplementedTask = StartAttacking.create(this::hasSpellBook, IAttackTask::findFirstValidAttackTarget);
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create(target -> farAway(target, maid));
        BehaviorControl<EntityMaid> moveToTargetTask = MaidRangedWalkToTarget.create(0.6f);
        BehaviorControl<EntityMaid> spellCastingTask = new FarSpellCombatBehavior();
        BehaviorControl<EntityMaid> strafingTask = new SpellStrafingTask();

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, moveToTargetTask),
                Pair.of(5, spellCastingTask),
                Pair.of(5, strafingTask)
        );
    }

    private class FarSpellCombatBehavior extends SpellCombatMeleeTask.UnifiedSpellCombatBehavior {
        private SimplifiedSpellCaster currentSpellCaster;

        private FarSpellCombatBehavior() {
            super();
        }

        @Override
        protected void start(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long gameTime) {
            // 设置女仆与玩家结盟，确保增益法术能正确识别友军
            AllianceManager.setMaidAlliance(maid, true);

            SimplifiedSpellCaster.clearLookTarget(maid);

            // 创建SpellCaster并设置初始目标
            currentSpellCaster = new SimplifiedSpellCaster(maid);

            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if(target == maid.getOwner() && ModList.get().isLoaded("irons_spellbooks")){
                target = MaidIronsSpellData.getOrCreate(maid).getOriginTarget();
            }
            if (!(target instanceof Player)) {
                currentSpellCaster.setTarget(target);
            }

        }

        @Override
        protected void tick(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long gameTime) {
            SimplifiedSpellCaster.clearLookTarget(maid);

            if (currentSpellCaster != null) {
                // 确保目标同步 - 这是唯一的目标更新点
                LivingEntity currentTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                if (!(currentTarget instanceof Player)) {
                    currentSpellCaster.setTarget(currentTarget);
                }
                currentSpellCaster.far_tick();
            }
        }
        
        @Override
        protected void stop(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long gameTime) {
            // 解除女仆与玩家的结盟
            AllianceManager.setMaidAlliance(maid, false);
            
            // 停止和清理SpellCaster
            if (currentSpellCaster != null) {
                currentSpellCaster = null;
            }
        }
    }

    class SpellStrafingTask extends SpellCombatMeleeTask.SpellStrafingTask {
        private boolean strafingClockwise;
        private boolean strafingBackwards;
        private int strafingTime = -1;
        private double optimalMinDistance = SimplifiedSpellCaster.FAR_RANGE; // 最佳最小距离
        private double maxAttackDistance = SPELL_RANGE;
        private double rangeRange = 5;

        public SpellStrafingTask() {
            super();
        }



        @Override
        protected void tick(ServerLevel worldIn, EntityMaid owner, long gameTime) {
            // 修正：检查法术书而不是投射武器
            SpellCombatFarTask task = new SpellCombatFarTask();
            if (!task.hasSpellBook(owner)) {
                return;
            }

            owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent((target) -> {
                double distance = owner.distanceTo(target);
                double optimalMaxDistance = optimalMinDistance + rangeRange; // 最佳最大距离

                // 如果在攻击距离内且能看到目标，开始计时
                if (distance < maxAttackDistance && owner.canSee(target)) {
                    ++this.strafingTime;
                } else {
                    this.strafingTime = -1;
                }

                // 随机改变走位方向，增加不可预测性
                if (this.strafingTime >= 20) {
                    if (owner.getRandom().nextFloat() < 0.3) {
                        this.strafingClockwise = !this.strafingClockwise;
                    }
                    if (owner.getRandom().nextFloat() < 0.3) {
                        this.strafingBackwards = !this.strafingBackwards;
                    }
                    this.strafingTime = 0;
                }

                // 执行走位逻辑
                if (this.strafingTime > -1) {
                    if (distance > optimalMaxDistance) {
                        this.strafingBackwards = false;
                    } else if (distance < optimalMinDistance) {
                        this.strafingBackwards = true;
                    }

                    float forwardSpeed = this.strafingBackwards ? -0.7F : 0.7F;
                    float strafeSpeed = this.strafingClockwise ? 0.7F : -0.7F;

                    owner.getMoveControl().strafe(forwardSpeed, strafeSpeed);
                    owner.setYRot(Mth.rotateIfNecessary(owner.getYRot(), owner.yHeadRot, 0.0F));

                }
                BehaviorUtils.lookAtEntity(owner, target);
            });
        }

    }
} 