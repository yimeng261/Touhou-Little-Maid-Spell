package com.github.yimeng261.maidspell.spell.providers;

import static com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkillHandler.armDirectSkillHitConfirmation;
import static com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkillHandler.collectNewDirectSkillAttackEntities;
import static com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkillHandler.describeTarget;
import static com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkillHandler.isValidTarget;
import static com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkillHandler.processPendingDirectSkillHit;
import static com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkills.adjustMaidLookAngle;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData;
import com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeComboMeta;
import com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkills;
import com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkills.DirectSkill;

import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.TargetSelector;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SlashBlade模组的法术提供者
 * 为女仆提供拔刀剑SA使用能力
 * 使用直接技能系统，不依赖原模组状态机
 */
public class SlashBladeProvider extends ISpellBookProvider<MaidSlashBladeData, ResourceLocation> {

    // ==================== 常量 ====================
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM = new Random();

    private static final int DASH_COUNT = 2;
    private static final double DIRECT_ATTACK_REACH_BUFFER = 0.75D;
    private static final int SLASH_ART_INTERVAL_TICKS = 4;
    private static final int DIRECT_SKILL_INTERVAL_TICKS = 3;
    private static final int MAX_COMBO_EXECUTION_TICKS = 200;
    private static final double SLASH_ART_RELEASE_RELOCK_EXTRA_RANGE = 2.0D;
    private static final float TARGET_HEALTH_INCREASE_LOG_THRESHOLD = 0.01F;
    private static final int RANDOM_JUMP_CHANCE = 15;
    private static final int COOLDOWN_FLOOR_THRESHOLD = -40;
    private static final int DEFAULT_COOLDOWN_TICKS = 100;

    private enum SlashBladeAction {
        NORMAL_COMBO,
        DIRECT_SKILL,
        SLASH_ART,
        MOVE_TO_TARGET
    }

    public SlashBladeProvider() {
        super(MaidSlashBladeData::getOrCreate, ResourceLocation.class);
    }

    // ==================== ISpellBookProvider API ====================

    @Override
    protected List<ResourceLocation> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<ResourceLocation> slashArts = new ArrayList<>();
        if (spellBook == null || spellBook.isEmpty() || !isSpellBook(spellBook)) {
            return slashArts;
        }
        BladeStateAccess.of(spellBook).ifPresent(state -> {
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
        logTargetHealthChange(maid, data, target, "setTarget");
        LivingEntity previousTarget = data.getTarget();
        if (previousTarget == null || target == null || previousTarget.getId() != target.getId()) {
            data.resetAttackSequence();
        }
        data.setTarget(target);

        ItemStack slashBlade = maid.getMainHandItem();
        if (isSpellBook(slashBlade)) {
            BladeStateAccess.of(slashBlade).ifPresent(state -> {
                state.setTargetEntityId(isValidTarget(target) ? target : null);
            });
        }
    }

    @Override
    public void initiateCasting(EntityMaid maid) {
        ItemStack itemStack = maid.getMainHandItem();
        MaidSlashBladeData data = getData(maid);
        LivingEntity target = data.getTarget();
        boolean hasSlashArt = hasSlashArt(itemStack);

        if (!isValidTarget(target)) {
            stopCasting(maid);
            return;
        }

        SlashBladeAction action = chooseCombatAction(maid, data, target, hasSlashArt);

        if (action == SlashBladeAction.NORMAL_COMBO) {
            performSlashBladeComboAttack(maid, itemStack, target);
            return;
        }
        if (action == SlashBladeAction.DIRECT_SKILL) {
            performSlashBladeAttack(maid, itemStack);
            return;
        }
        if (action == SlashBladeAction.MOVE_TO_TARGET) {
            maid.getNavigation().moveTo(target, 0.6);
            return;
        }

        // SlashArt: 开始蓄力
        BladeStateAccess.of(itemStack).ifPresent(state -> {
            if (state.isBroken() || state.isSealed()) {
                return;
            }
            maid.startUsingItem(InteractionHand.MAIN_HAND);
            int targetUseTime = state.getFullChargeTicks(maid) + SlashArts.getJustReceptionSpan(maid) / 2;
            data.setCasting(true);
            data.setSAExecutionStartTime(maid.level().getGameTime());
            data.setTargetUseTime(targetUseTime);
        });
    }

    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidSlashBladeData data = getData(maid);
        processPendingDirectSkillHit(maid, data);
        LivingEntity target = data.getTarget();

        if (target != null && !isValidTarget(target)) {
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            stopCasting(maid);
            return;
        }

        ItemStack slashBlade = maid.getMainHandItem();
        if (!isSpellBook(slashBlade)) {
            if (data.isCasting()) {
                LOGGER.warn("[MaidSpell][SlashBlade] continuous aborted: maid={} mainHand is not slashblade: {}", maid.getId(), slashBlade.getItem());
            }
            return;
        }

        if (!data.isCasting()) {
            return;
        }

        // 蓄力阶段
        if (maid.isUsingItem()) {
            int ticksUsing = maid.getTicksUsingItem();
            if (ticksUsing >= data.getTargetUseTime()) {
                triggerSlashArt(maid, slashBlade);
            } else {
                LivingEntity chargeTarget = data.getTarget();
                if (isValidTarget(chargeTarget)) {
                    BehaviorUtils.lookAtEntity(maid, chargeTarget);
                    Vec3 targetEyePos = chargeTarget.getEyePosition();
                    maid.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetEyePos);
                } else {
                    stopCasting(maid);
                }
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

        ItemStack slashBlade = maid.getMainHandItem();
        if (isSpellBook(slashBlade)) {
            BladeStateAccess.of(slashBlade).ifPresent(state -> {
                state.updateComboSeq(maid, ComboStateRegistry.NONE.getId());
                state.setTargetEntityId((Entity) null);
            });
        }

        data.resetCastingState();
        if (data.getCooldown() < COOLDOWN_FLOOR_THRESHOLD) {
            data.setCooldown(DEFAULT_COOLDOWN_TICKS);
        }
    }

    // ==================== 战斗决策 ====================

    private SlashBladeAction chooseCombatAction(EntityMaid maid, MaidSlashBladeData data, LivingEntity target, boolean hasSlashArt) {
        double distance = maid.distanceTo(target);
        if (distance > getReliableDirectAttackReach(maid)) {
            return SlashBladeAction.MOVE_TO_TARGET;
        }
        if (!maid.onGround()) {
            return distance < SimplifiedSpellCaster.MELEE_RANGE ? SlashBladeAction.NORMAL_COMBO : SlashBladeAction.DIRECT_SKILL;
        }
        if (shouldUseSlashArt(maid, data, hasSlashArt)) {
            return SlashBladeAction.SLASH_ART;
        }
        if (shouldUseDirectSkill(maid, data)) {
            return SlashBladeAction.DIRECT_SKILL;
        }
        return SlashBladeAction.NORMAL_COMBO;
    }

    private boolean shouldUseSlashArt(EntityMaid maid, MaidSlashBladeData data, boolean hasSlashArt) {
        return hasSlashArt && !data.isOnCooldown() && data.getAttackSequence() > 0
                && data.getAttackSequence() % SLASH_ART_INTERVAL_TICKS == 0;
    }

    private boolean shouldUseDirectSkill(EntityMaid maid, MaidSlashBladeData data) {
        return data.getAttackSequence() > 0 && data.getAttackSequence() % DIRECT_SKILL_INTERVAL_TICKS == 0;
    }

    // ==================== Slash Art / Combo 执行 ====================

    private void triggerSlashArt(EntityMaid maid, ItemStack slashBlade) {
        MaidSlashBladeData data = getData(maid);
        LivingEntity target = data.getTarget();

        if (!isValidTarget(target)) {
            stopCasting(maid);
            return;
        }

        double releaseDistance = maid.distanceTo(target);
        double releaseMaxDistance = getReliableDirectAttackReach(maid) + SLASH_ART_RELEASE_RELOCK_EXTRA_RANGE;
        if (releaseDistance > releaseMaxDistance) {
            stopCasting(maid);
            return;
        }

        adjustMaidLookAngle(maid, target);
        BladeStateAccess.of(slashBlade).ifPresent(state -> state.setTargetEntityId(target));

        int ticksUsing = maid.getTicksUsingItem();
        int useDuration = slashBlade.getUseDuration(maid);
        int timeLeft = Math.max(0, useDuration - ticksUsing);

        slashBlade.releaseUsing(maid.level(), maid, timeLeft);
        maid.stopUsingItem();

        BladeStateAccess.of(slashBlade).ifPresent(state -> {
            ResourceLocation currentCombo = state.getComboSeq();
            if (!currentCombo.equals(ComboStateRegistry.NONE.getId())) {
                data.setSAExecutionStartTime(maid.level().getGameTime());
                data.incrementAttackSequence();
            } else {
                stopCasting(maid);
            }
        });
    }

    public boolean hasSlashArt(ItemStack itemStack) {
        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof ItemSlashBlade)) {
            return false;
        }
        return BladeStateAccess.of(itemStack).map(state ->
                state.getSlashArtsKey() != null && !state.getSlashArtsKey().equals(SlashArtsRegistry.NONE.getId())
        ).orElse(false);
    }

    private void processComboExecution(EntityMaid maid, ItemStack slashBlade) {
        MaidSlashBladeData data = getData(maid);

        long currentTime = maid.level().getGameTime();
        long executionTime = currentTime - data.getSAExecutionStartTime();
        if (executionTime > MAX_COMBO_EXECUTION_TICKS) {
            stopCasting(maid);
            return;
        }

        BladeStateAccess.of(slashBlade).ifPresent(state -> {
            ResourceLocation currentCombo = state.getComboSeq();
            if (currentCombo.equals(ComboStateRegistry.NONE.getId())) {
                stopCasting(maid);
                return;
            }

            ResourceLocation lastCombo = data.getLastComboState();
            data.setLastComboState(currentCombo);

            ComboState comboState = ComboStateRegistry.REGISTRY.get(currentCombo);
            if (comboState == null) {
                LOGGER.warn("[MaidSpell][SlashBlade] combo missing from registry: maid={} combo={}", maid.getId(), currentCombo);
                stopCasting(maid);
                return;
            }

            if (data.isNormalComboCasting() && isNormalComboFollowThrough(currentCombo, lastCombo)) {
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                stopCasting(maid);
                return;
            }

            long comboElapsedTicks = ComboState.getElapsed(maid);
            if (!data.isNormalComboCasting() && isSlashArtRecovery(currentCombo, comboElapsedTicks)) {
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                stopCasting(maid);
                return;
            }

            slashBlade.getItem().inventoryTick(slashBlade, maid.level(), maid, 0, true);
        });
    }

    private SlashBladeComboMeta.Entry getSlashArtComboMeta(ResourceLocation currentCombo) {
        return SlashBladeComboMeta.get(currentCombo);
    }

    private boolean isSlashArtRecovery(ResourceLocation currentCombo, long comboElapsedTicks) {
        return getSlashArtComboMeta(currentCombo).canCancel(comboElapsedTicks);
    }

    private boolean isNormalComboFollowThrough(ResourceLocation currentCombo, ResourceLocation lastCombo) {
        String path = currentCombo.getPath();
        return path.endsWith("_end") || path.endsWith("_end2") || (lastCombo != null && lastCombo.equals(currentCombo));
    }

    // ==================== 普通 Combo 攻击 ====================

    private void performSlashBladeComboAttack(EntityMaid maid, ItemStack slashBlade, LivingEntity target) {
        if (!isSpellBook(slashBlade) || !isValidTarget(target)) {
            return;
        }

        MaidSlashBladeData data = getData(maid);
        adjustMaidLookAngle(maid, target);

        BladeStateAccess.of(slashBlade).ifPresent(state -> {
            state.setTargetEntityId(target);
            IInputState input = maid.getData(CapabilityInputState.INPUT_STATE.get());
            if (input != null) {
                boolean addedClick = input.getCommands().add(InputCommand.L_CLICK);
                InputCommand stanceCommand = maid.onGround() ? InputCommand.ON_GROUND : InputCommand.ON_AIR;
                InputCommand oppositeStanceCommand = maid.onGround() ? InputCommand.ON_AIR : InputCommand.ON_GROUND;
                boolean addedStance = input.getCommands().add(stanceCommand);
                boolean removedOpposite = input.getCommands().remove(oppositeStanceCommand);
                try {
                    state.progressCombo(maid);
                } finally {
                    if (addedClick) {
                        input.getCommands().remove(InputCommand.L_CLICK);
                    }
                    if (addedStance) {
                        input.getCommands().remove(stanceCommand);
                    }
                    if (removedOpposite) {
                        input.getCommands().add(oppositeStanceCommand);
                    }
                }
            }

            ResourceLocation after = state.getComboSeq();
            if (!after.equals(ComboStateRegistry.NONE.getId())) {
                data.setCasting(true);
                data.setSAExecutionStartTime(maid.level().getGameTime());
                data.setLastComboState(null);
                data.setNormalComboCasting(true);
                data.incrementAttackSequence();
                slashBlade.getItem().inventoryTick(slashBlade, maid.level(), maid, 0, true);
            }
        });
    }

    // ==================== 直接技能攻击 ====================

    private void performSlashBladeAttack(EntityMaid maid, ItemStack slashBlade) {
        MaidSlashBladeData data = getData(maid);
        LivingEntity target = data.getTarget();

        if (!isValidTarget(target)) {
            return;
        }

        adjustMaidLookAngle(maid, target);

        // 随机触发跳跃
        if (maid.onGround() && RANDOM.nextInt(100) < RANDOM_JUMP_CHANCE) {
            SlashBladeDirectSkills.updateJumpHeightFromTarget(data);
            double jumpHeight = data.getTarget() != null ? data.getTarget().getBbHeight() / 2.0D : 1.2D;
            Vec3 jumpMotion = new Vec3(0, jumpHeight * 0.4D, 0);
            maid.setDeltaMovement(maid.getDeltaMovement().add(jumpMotion));
            maid.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 100, 0, false, false, true));
        }

        DirectSkill skill = selectRandomSkill(maid);

        // 更新冲刺计数器
        if (SlashBladeDirectSkills.ALL_DASH_SKILLS.contains(skill)) {
            data.resetNonDashSkillCount();
        } else {
            data.incrementNonDashSkillCount();
        }

        List<Integer> attackEntityIds = collectNewDirectSkillAttackEntities(maid, () -> skill.execute(maid));
        armDirectSkillHitConfirmation(maid, data, target, skill, attackEntityIds);
        data.incrementAttackSequence();

        BladeStateAccess.of(slashBlade).ifPresent(state ->
                state.setLastActionTime(maid.level().getGameTime()));
    }

    private DirectSkill selectRandomSkill(EntityMaid maid) {
        MaidSlashBladeData data = getData(maid);

        if (data.getNonDashSkillCount() >= DASH_COUNT && maid.onGround()) {
            return SlashBladeDirectSkills.ALL_DASH_SKILLS.get(RANDOM.nextInt(SlashBladeDirectSkills.ALL_DASH_SKILLS.size()));
        }

        List<DirectSkill> availableSkills;
        if (maid.onGround()) {
            int roll = RANDOM.nextInt(100);
            if (roll < 60) {
                availableSkills = SlashBladeDirectSkills.GROUND_BASIC_SKILLS;
            } else if (roll < 80) {
                availableSkills = SlashBladeDirectSkills.GROUND_COMBO_SKILLS;
            } else {
                availableSkills = SlashBladeDirectSkills.GROUND_SPECIAL_SKILLS;
            }
        } else {
            availableSkills = SlashBladeDirectSkills.AERIAL_SKILLS;
        }

        return availableSkills.get(RANDOM.nextInt(availableSkills.size()));
    }

    // ==================== 工具方法 ====================

    private double getReliableDirectAttackReach(EntityMaid maid) {
        return Math.max(SimplifiedSpellCaster.MELEE_RANGE, TargetSelector.getResolvedReach(maid) + DIRECT_ATTACK_REACH_BUFFER);
    }

    private void logTargetHealthChange(EntityMaid maid, MaidSlashBladeData data, LivingEntity target, String phase) {
        if (target == null) {
            data.setLastTargetEntityId(-1);
            data.setLastObservedTargetHealth(Float.NaN);
            return;
        }
        int targetId = target.getId();
        float currentHealth = target.getHealth();
        if (data.getLastTargetEntityId() != targetId || Float.isNaN(data.getLastObservedTargetHealth())) {
            data.setLastTargetEntityId(targetId);
            data.setLastObservedTargetHealth(currentHealth);
            return;
        }
        float previousHealth = data.getLastObservedTargetHealth();
        if (currentHealth > previousHealth + TARGET_HEALTH_INCREASE_LOG_THRESHOLD) {
            LOGGER.warn("[MaidSpell][SlashBlade] target health increased: maid={} phase={} target={} previousHealth={} currentHealth={} delta={}",
                    maid.getId(), phase, describeTarget(target), previousHealth, currentHealth, currentHealth - previousHealth);
        }
        data.setLastObservedTargetHealth(currentHealth);
    }
}
