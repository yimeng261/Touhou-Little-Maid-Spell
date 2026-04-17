package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.base;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import io.redspace.ironsspellbooks.IronsSpellbooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.IAnimatedAttacker;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.NeutralWizard;
import io.redspace.ironsspellbooks.entity.mobs.goals.PatrolNearLocationGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardRecoverGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.melee.AttackAnimationData;
import io.redspace.ironsspellbooks.entity.mobs.wizards.GenericAnimatedWarlockAttackGoal;
import io.redspace.ironsspellbooks.entity.mobs.wizards.fire_boss.NotIdioticNavigation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class AbstractSpellMeleeMob extends NeutralWizard implements IAnimatedAttacker {
    private static final ResourceLocation CLAYMORE_ID = ResourceLocation.fromNamespaceAndPath(IronsSpellbooksCompat.MOD_ID, "claymore");

    private RawAnimation animationToPlay = null;
    private final AnimationController<AbstractSpellMeleeMob> meleeController =
            new AnimationController<>(this, "melee_animations", 0, this::predicate);

    protected AbstractSpellMeleeMob(EntityType<? extends AbstractSpellCastingMob> entityType, Level level) {
        super(entityType, level);
        this.xpReward = getXpRewardValue();
        this.lookControl = this.createLookControl();
        this.moveControl = this.createMoveControl();
    }

    protected int getXpRewardValue() {
        return 25;
    }

    protected boolean shouldPatrol() {
        return true;
    }

    protected float patrolRange() {
        return 30.0f;
    }

    protected double patrolSpeed() {
        return 0.75;
    }

    protected boolean addDefaultPlayerTargetGoal() {
        return true;
    }

    protected boolean isDefaultPlayerTargetHostile(LivingEntity target) {
        return this.isHostileTowards(target);
    }

    protected List<AttackAnimationData> getMeleeMoveset() {
        return List.of(
                new AttackAnimationData(9, "simple_sword_upward_swipe", 5),
                new AttackAnimationData(8, "simple_sword_lunge_stab", 6),
                new AttackAnimationData(10, "simple_sword_stab_alternate", 8),
                new AttackAnimationData(10, "simple_sword_horizontal_cross_swipe", 8));
    }

    protected float getComboChance() {
        return 0.45f;
    }

    protected int getSpellAttackIntervalMin() {
        return 40;
    }

    protected int getSpellAttackIntervalMax() {
        return 70;
    }

    protected int getMeleeAttackIntervalMin() {
        return 10;
    }

    protected int getMeleeAttackIntervalMax() {
        return 24;
    }

    protected float getMeleeMovespeedModifier() {
        return 1.5f;
    }

    protected double getCombatGoalSpeedModifier() {
        return 1.25;
    }

    protected float getMeleeBiasMin() {
        return 0.25f;
    }

    protected float getMeleeBiasMax() {
        return 0.75f;
    }

    protected static Item getClaymoreItem() {
        return Objects.requireNonNull(BuiltInRegistries.ITEM.get(CLAYMORE_ID));
    }

    protected abstract List<AbstractSpell> getAttackSpells();

    protected List<AbstractSpell> getDefenseSpells() {
        return Collections.emptyList();
    }

    protected abstract List<AbstractSpell> getMovementSpells();

    protected abstract List<AbstractSpell> getSupportSpells();

    @Nullable
    protected AbstractSpell getSingleUseSpell() {
        return null;
    }

    protected int getSingleUseMinDelay() {
        return 120;
    }

    protected int getSingleUseMaxDelay() {
        return 240;
    }

    protected int getSingleUseMinLevel() {
        return 1;
    }

    protected int getSingleUseMaxLevel() {
        return 3;
    }

    @Override
    protected LookControl createLookControl() {
        return new LookControl(this) {
            @Override
            protected float rotateTowards(float from, float to, float maxDelta) {
                return super.rotateTowards(from, to, maxDelta * 2.5f);
            }

            @Override
            protected boolean resetXRotOnTick() {
                return AbstractSpellMeleeMob.this.getTarget() == null;
            }
        };
    }

    protected MoveControl createMoveControl() {
        return new MoveControl(this) {
            @Override
            protected float rotlerp(float sourceAngle, float targetAngle, float maximumChange) {
                double dx = this.wantedX - this.mob.getX();
                double dz = this.wantedZ - this.mob.getZ();
                if (dx * dx + dz * dz < 0.5) {
                    return sourceAngle;
                }
                return super.rotlerp(sourceAngle, targetAngle, maximumChange * 0.25f);
            }
        };
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, createCombatGoal());
        if (shouldPatrol()) {
            this.goalSelector.addGoal(4, new PatrolNearLocationGoal(this, patrolRange(), patrolSpeed()));
        }
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(10, new WizardRecoverGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        if (addDefaultPlayerTargetGoal()) {
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isDefaultPlayerTargetHostile));
            this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));
        }
        registerAdditionalGoals();
    }

    protected void registerAdditionalGoals() {
    }

    protected GenericAnimatedWarlockAttackGoal<? extends AbstractSpellMeleeMob> createCombatGoal() {
        GenericAnimatedWarlockAttackGoal<AbstractSpellMeleeMob> goal = new GenericAnimatedWarlockAttackGoal<>(
                this,
                getCombatGoalSpeedModifier(),
                getSpellAttackIntervalMin(),
                getSpellAttackIntervalMax());
        goal.setMoveset(getMeleeMoveset());
        goal.setComboChance(getComboChance());
        goal.setMeleeBias(getMeleeBiasMin(), getMeleeBiasMax());
        goal.setMeleeAttackInverval(getMeleeAttackIntervalMin(), getMeleeAttackIntervalMax());
        goal.setMeleeMovespeedModifier(getMeleeMovespeedModifier());
        goal.setSpells(getAttackSpells(), getDefenseSpells(), getMovementSpells(), getSupportSpells());

        AbstractSpell singleUseSpell = getSingleUseSpell();
        if (singleUseSpell != null) {
            goal.setSingleUseSpell(singleUseSpell, getSingleUseMinDelay(), getSingleUseMaxDelay(), getSingleUseMinLevel(), getSingleUseMaxLevel());
        }
        return goal;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData) {
        this.populateDefaultEquipmentSlots(this.getRandom(), difficulty);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    protected void equipAndHideDrop(EquipmentSlot slot, ItemStack stack) {
        this.setItemSlot(slot, stack);
        this.setDropChance(slot, 0.0f);
    }

    @Override
    public boolean isHostileTowards(LivingEntity target) {
        if (this instanceof Enemy && target instanceof Player) {
            return true;
        }
        return super.isHostileTowards(target);
    }

    @Override
    public boolean shouldSheathSword() {
        return true;
    }

    @Override
    public void playAnimation(String animationId) {
        try {
            this.animationToPlay = RawAnimation.begin().thenPlay(animationId);
        } catch (Exception ignored) {
            IronsSpellbooks.LOGGER.error("Entity {} Failed to play animation: {}", this, animationId);
        }
    }

    private PlayState predicate(AnimationState<AbstractSpellMeleeMob> animationEvent) {
        AnimationController<AbstractSpellMeleeMob> controller = animationEvent.getController();
        if (this.animationToPlay != null) {
            controller.forceAnimationReset();
            controller.setAnimation(this.animationToPlay);
            this.animationToPlay = null;
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(this.meleeController);
        super.registerControllers(controllerRegistrar);
    }

    @Override
    public boolean isAnimating() {
        return this.meleeController.getAnimationState() != AnimationController.State.STOPPED || super.isAnimating();
    }

    @Override
    public boolean guardsBlocks() {
        return false;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NotIdioticNavigation(this, level);
    }
}
