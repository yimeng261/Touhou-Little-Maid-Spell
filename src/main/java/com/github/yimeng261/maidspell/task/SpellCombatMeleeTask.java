package com.github.yimeng261.maidspell.task;

import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.github.yimeng261.maidspell.spell.manager.AllianceManager;

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
public class SpellCombatMeleeTask implements IRangedAttackTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation UID = new ResourceLocation("maidspell", "spell_combat_melee");
    private static final MutableComponent NAME = Component.translatable("task.maidspell.spell_combat");
    private static float SPELL_RANGE;

    public static void setSpellRange(Float range){
        SPELL_RANGE = range;
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false; // 对于战斗任务，通常禁用随机行为以保持专注
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
        BehaviorControl<EntityMaid> moveToTargetTask = MaidRangedWalkToTarget.create(0.6f);
        BehaviorControl<EntityMaid> spellCastingTask = new UnifiedSpellCombatBehavior();
        BehaviorControl<EntityMaid> strafingTask = new SpellStrafingTask();

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, moveToTargetTask),
                Pair.of(5, spellCastingTask),
                Pair.of(5, strafingTask)
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
    }


    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        return maid.distanceTo(target) <= SPELL_RANGE*1.2 && Math.abs(maid.getY()-target.getY()) < 3.5;
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        float searchRange = SPELL_RANGE;
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
    boolean hasSpellBook(EntityMaid maid) {
        return true;
    }

    /**
     * 检查目标是否距离过远
     */
    boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > this.searchRadius(maid);
    }


    /**
     * 统一的法术战斗行为控制器
     * 负责管理SimplifiedSpellCaster，并确保目标同步
     */
    class UnifiedSpellCombatBehavior extends Behavior<EntityMaid> {
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
                    currentSpellCaster.melee_tick();
                }
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

    static class SpellStrafingTask extends Behavior<EntityMaid> {
        private boolean strafingClockwise;
        private boolean strafingBackwards;
        private int strafingTime = -1;
        private double optimalMinDistance = SimplifiedSpellCaster.MELEE_RANGE - 1; // 最佳最小距离
        private double maxAttackDistance = SPELL_RANGE;
        private double rangeRange = 2;

        public SpellStrafingTask() {
            super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                            MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                            MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT),
                    1200);
        }


        @Override
        protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
            // 修正：检查是否有法术书而不是投射武器
            SpellCombatFarTask task = new SpellCombatFarTask();
            return task.hasSpellBook(owner) &&
                    owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                            .filter(Entity::isAlive)
                            .isPresent();
        }

        @Override
        protected void tick(ServerLevel worldIn, EntityMaid owner, long gameTime) {
            // 修正：检查法术书而不是投射武器
            SpellCombatMeleeTask task = new SpellCombatMeleeTask();
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

                    float forwardSpeed = this.strafingBackwards ? -0.7F : 0.5F;
                    float strafeSpeed = this.strafingClockwise ? 0.7F : -0.7F;

                    owner.getMoveControl().strafe(forwardSpeed, strafeSpeed);
                    owner.setYRot(Mth.rotateIfNecessary(owner.getYRot(), owner.yHeadRot, 0.0F));
                }
                BehaviorUtils.lookAtEntity(owner, target);

            });
        }

        @Override
        protected void start(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
            entityIn.setSwingingArms(true);
        }

        @Override
        protected void stop(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
            entityIn.setSwingingArms(false);
            entityIn.getMoveControl().strafe(0, 0);
        }

        @Override
        protected boolean canStillUse(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
            return this.checkExtraStartConditions(worldIn, entityIn);
        }
    }
} 