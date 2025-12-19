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
import net.minecraft.commands.arguments.EntityAnchorArgument;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 法术战斗任务 - 统一的索敌和战斗管理
 * 实现IRangedAttackTask接口，为女仆提供法术攻击能力和混合战斗模式
 * 负责统一的目标搜索、验证和管理
 */
public class SpellCombatMeleeTask implements IRangedAttackTask {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogUtils.getLogger();
    @SuppressWarnings("removal")
    public static final ResourceLocation UID = new ResourceLocation("maidspell", "spell_combat_melee");
    private static final MutableComponent NAME = Component.translatable("task.maidspell.spell_combat");
    private static float SPELL_RANGE;

    public static void setSpellRange(Float range){
        SPELL_RANGE = range;
    }

    @Override
    public boolean enableLookAndRandomWalk(@NotNull EntityMaid maid) {
        return false; // 对于战斗任务，通常禁用随机行为以保持专注
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
    public @NotNull ItemStack getIcon() {
        return Items.ENCHANTED_BOOK.getDefaultInstance();
    }

    @Override
    public SoundEvent getAmbientSound(@NotNull EntityMaid maid) {
        // 使用女仆远程攻击音效
        return SoundUtil.attackSound(maid, InitSounds.MAID_RANGE_ATTACK.get(), 0.5f);
    }

    @Override
    public boolean isEnable(@NotNull EntityMaid maid) {
        return true;
    }



    @Override
    public @NotNull List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(@NotNull EntityMaid maid) {
        // 使用近战索敌方法 - 它已经内置了优先攻击最近目标的逻辑
        BehaviorControl<EntityMaid> supplementedTask = StartAttacking.create(this::hasSpellBook, IAttackTask::findFirstValidAttackTarget);
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create(target -> farAway(target, maid));
        BehaviorControl<EntityMaid> moveToTargetTask = MaidRangedWalkToTarget.create(0.6f);
        BehaviorControl<EntityMaid> spellCastingTask = new SpellCombatBehavior();
        BehaviorControl<EntityMaid> strafingTask = new SpellStrafingTask();
        // 添加高优先级的视线控制任务，阻止女仆看向玩家
        BehaviorControl<EntityMaid> lookControlTask = new CombatLookControlTask();

        return Lists.newArrayList(
                Pair.of(1, lookControlTask),      // 最高优先级，控制视线
                Pair.of(2, supplementedTask),     // 提高战斗行为优先级
                Pair.of(2, findTargetTask),
                Pair.of(2, moveToTargetTask),
                Pair.of(2, spellCastingTask),
                Pair.of(2, strafingTask)
        );
    }

    @Override
    public void performRangedAttack(@NotNull EntityMaid shooter, @NotNull LivingEntity target, float distanceFactor) {
    }


    @Override
    public boolean canSee(EntityMaid maid, @NotNull LivingEntity target) {
        return maid.distanceTo(target) <= SPELL_RANGE * 1.2 && Math.abs(maid.getY() - target.getY()) < 5 && !(target instanceof Player);
    }

    @Override
    public @NotNull AABB searchDimension(EntityMaid maid) {
        float searchRange = SPELL_RANGE;
        return maid.hasRestriction()
            ? new AABB(maid.getRestrictCenter()).inflate(searchRange)
            : maid.getBoundingBox().inflate(searchRange);
    }

    @Override
    public float searchRadius(@NotNull EntityMaid maid) {
        return SPELL_RANGE;
    }

    @Override
    public boolean hasExtraAttack(@NotNull EntityMaid maid, @NotNull Entity target) {
        return hasSpellBook(maid) && target instanceof LivingEntity && maid.distanceTo(target) <= SPELL_RANGE && !(target instanceof Player);
    }

    @Override
    public @NotNull List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(@NotNull EntityMaid maid) {
        return Lists.newArrayList(
                Pair.of("spell_book", this::hasSpellBook)
        );
    }

    @Override
    public boolean isWeapon(@NotNull EntityMaid maid, @NotNull ItemStack stack) {
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
    static class SpellCombatBehavior extends Behavior<EntityMaid> {
        private SimplifiedSpellCaster currentSpellCaster;

        public SpellCombatBehavior() {
            super(Map.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
            ));
        }

        @Override
        protected boolean checkExtraStartConditions(@NotNull ServerLevel level, EntityMaid maid) {
            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (target == null || !target.isAlive() || target instanceof Player) {
                return false;
            }

            double distance = maid.distanceTo(target);
            return distance <= SPELL_RANGE;
        }

        @Override
        protected boolean canStillUse(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            return checkExtraStartConditions(level, maid);
        }

        @Override
        protected void start(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            // 不再清理LookTarget，让CombatLookControlTask处理
            // SimplifiedSpellCaster.clearLookTarget(maid);

            // 创建SpellCaster并设置初始目标
            this.currentSpellCaster = new SimplifiedSpellCaster(maid);

            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if(target == maid.getOwner() && ModList.get().isLoaded("irons_spellbooks")){
                target = MaidIronsSpellData.getOrCreate(maid).getOriginTarget();
            }
            if (!(target instanceof Player)) {
                this.currentSpellCaster.setTarget(target);
            }
        }


        @Override
        protected void tick(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            // 不再主动清理LookTarget，让CombatLookControlTask统一管理
            // SimplifiedSpellCaster.clearLookTarget(maid);
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
        protected void stop(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            // 停止和清理SpellCaster
            if (currentSpellCaster != null) {
                currentSpellCaster = null;
            }
        }
    }

    /**
     * 战斗状态下的视线控制任务
     * 高优先级任务，确保女仆在战斗时只看向攻击目标，不会看向玩家
     * 特殊情况：当女仆释放蓝色音符标记的铁魔法法术时，允许看向主人
     */
    static class CombatLookControlTask extends Behavior<EntityMaid> {
        
        public CombatLookControlTask() {
            super(Map.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
            ));
        }

        @Override
        protected boolean checkExtraStartConditions(@NotNull ServerLevel level, EntityMaid maid) {
            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            // 如果是铁魔法特殊情况（目标是主人），也允许启动
            if (target instanceof Player && isIronSpellSpecialCase(maid)) {
                return true;
            }
            return target != null && target.isAlive() && !(target instanceof Player);
        }

        @Override
        protected boolean canStillUse(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            return checkExtraStartConditions(level, maid);
        }

        @Override
        protected void tick(@NotNull ServerLevel level, EntityMaid maid, long gameTime) {
            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (target != null && target.isAlive()) {
                // 检查是否是铁魔法特殊情况
                if (target instanceof Player && isIronSpellSpecialCase(maid)) {
                    BehaviorUtils.lookAtEntity(maid, target);
                    maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0.0F));
                    maid.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                } else if (!(target instanceof Player)) {
                    maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                    BehaviorUtils.lookAtEntity(maid, target);
                    maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0.0F));
                    maid.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                }
            }
        }


        @Override
        protected void start(@NotNull ServerLevel level, EntityMaid maid, long gameTime) {
            // 开始时，只有在非特殊情况下才清除干扰视线目标
            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (!(target instanceof Player && isIronSpellSpecialCase(maid))) {
                maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            }
        }

        @Override
        protected void stop(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            // 战斗结束时不做特殊处理，让其他系统接管
        }
        
        /**
         * 检查是否是铁魔法特殊情况（蓝色音符标记的法术）
         * 这种情况下女仆需要对主人施法，因此应该看向主人
         */
        private boolean isIronSpellSpecialCase(EntityMaid maid) {
            if (!ModList.get().isLoaded("irons_spellbooks")) {
                return false;
            }
            
            try {
                // 获取女仆的铁魔法数据
                MaidIronsSpellData data = MaidIronsSpellData.getOrCreate(maid);
                if (data == null) {
                    return false;
                }
                
                // 检查当前目标是否是原始目标切换到主人的结果
                // 这表明女仆正在施放蓝色音符标记的法术
                LivingEntity currentTarget = data.getTarget();
                LivingEntity originTarget = data.getOriginTarget();
                LivingEntity owner = maid.getOwner();
                
                return currentTarget == owner && originTarget != null && originTarget != owner;
                
            } catch (Exception e) {
                // 如果出现任何异常，安全返回false
                return false;
            }
        }
    }

    static class SpellStrafingTask extends Behavior<EntityMaid> {
        protected boolean strafingClockwise;
        protected boolean strafingBackwards;
        protected int strafingTime = -1;
        private final double optimalMinDistance = SimplifiedSpellCaster.MELEE_RANGE - 1; // 最佳最小距离
        private final double maxAttackDistance = SPELL_RANGE;
        private final double rangeRange = 1.5;

        public SpellStrafingTask() {
            super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                            MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                            MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT),
                    1200);
        }


        @Override
        protected boolean checkExtraStartConditions(@NotNull ServerLevel worldIn, EntityMaid maid) {
            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            return target != null && target.isAlive() && !(target instanceof Player);
        }

        @Override
        protected void tick(@NotNull ServerLevel worldIn, EntityMaid maid, long gameTime) {
            // 如果女仆处于坐下状态，不执行走位逻辑
            if (maid.isOrderedToSit()) {
                return;
            }

            maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent((target) -> {
                double distance = maid.distanceTo(target);
                double optimalMaxDistance = this.optimalMinDistance + this.rangeRange; // 最佳最大距离

                // 如果在攻击距离内且能看到目标，开始计时
                if (distance < this.maxAttackDistance && maid.canSee(target)) {
                    ++this.strafingTime;
                } else {
                    this.strafingTime = -1;
                }

                // 随机改变走位方向，增加不可预测性
                if (this.strafingTime >= 20) {
                    if (maid.getRandom().nextFloat() < 0.3) {
                        this.strafingClockwise = !this.strafingClockwise;
                    }
                    if (maid.getRandom().nextFloat() < 0.3) {
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

                    maid.getMoveControl().strafe(forwardSpeed, strafeSpeed);
                    maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0.0F));
                }


            });
        }

        @Override
        protected void start(@NotNull ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
            entityIn.setSwingingArms(true);
        }

        @Override
        protected void stop(@NotNull ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
            entityIn.setSwingingArms(false);
            entityIn.getMoveControl().strafe(0, 0);
        }

        @Override
        protected boolean canStillUse(@NotNull ServerLevel worldIn, @NotNull EntityMaid entityIn, long gameTimeIn) {
            return this.checkExtraStartConditions(worldIn, entityIn);
        }
    }
} 