package com.github.yimeng261.maidspell.spell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.ModList;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;

/**
 * 简化版的女仆法术施放AI - 不再独立处理索敌，依赖外部传入目标
 * 目标管理统一使用 Brain 的 ATTACK_TARGET 记忆
 */
public class SimplifiedSpellCaster {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogUtils.getLogger();
    private final EntityMaid maid;
    private long lastMeleeAttemptTick = -1L;
    private long lastFarAttemptTick = -1L;
    private long lastMeleeExecuteTick = -1L;
    private long lastFarExecuteTick = -1L;

    public static double MELEE_RANGE;
    public static double FAR_RANGE;

    private final SpellBookManager spellBookManager;

    public SimplifiedSpellCaster(EntityMaid maid) {
        this.maid = maid;
        this.spellBookManager = SpellBookManager.getOrCreateManager(maid);
    }

    /**
     * 设置当前攻击目标到 SpellBookManager
     * @param target 攻击目标
     */
    public void setTarget(LivingEntity target) {
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        //LOGGER.debug("[MaidSpell][Caster] setTarget maid={} tick={} target={}", maid.getId(), maid.tickCount, describeTarget(target));
        if (spellBookManager != null && target != null && !(target instanceof Player)) {
            for (ISpellBookProvider<?, ?> provider : spellBookManager.getProviders()) {
                provider.setTarget(maid, target);
            }
        }
    }

    /**
     * 检查是否有有效目标
     * 从 Brain 的 ATTACK_TARGET 记忆中读取
     */
    public boolean hasValidTarget() {
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        boolean valid = target != null && target.isAlive() && !target.isDeadOrDying() && !target.isRemoved();
        if (!valid) {
            if (target != null) {
                //LOGGER.debug("[MaidSpell][Caster] clearing invalid target maid={} tick={} target={}", maid.getId(), maid.tickCount, describeTarget(target));
                maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else if (maid.tickCount % 20 == 0) {
                //LOGGER.debug("[MaidSpell][Caster] invalid target maid={} tick={} target=null", maid.getId(), maid.tickCount);
            }
        }
        return valid;
    }

    /**
     * 执行施法逻辑
     */
    public void melee_tick() {

        if (!hasValidTarget()) {
            return; // 没有有效目标，退出
        }

        if (maid.tickCount % Config.meleeAttackInterval == 0) {
            long delta = lastMeleeAttemptTick < 0 ? -1 : maid.tickCount - lastMeleeAttemptTick;
            lastMeleeAttemptTick = maid.tickCount;
            // 执行战斗逻辑
            clearLookTarget(maid);
            LivingEntity target = maid.getTarget();
            if (target != null) {
                double distance = maid.distanceTo(target);
                executeCombat(distance);
            } else {
                LOGGER.debug("[MaidSpell][Caster] melee skipped maid={} tick={} reason=no mob target", maid.getId(), maid.tickCount);
            }
        }
    }

    /**
     * 执行施法逻辑
     */
    public void far_tick() {

        if (!hasValidTarget()) {
            return; // 没有有效目标，退出
        }

        if (maid.tickCount % Config.farAttackInterval == 0) {
            lastFarAttemptTick = maid.tickCount;
            clearLookTarget(maid);
            executeCombatFar();
        }
    }

    public static void clearLookTarget(EntityMaid maid) {
        maid.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).ifPresent(lookTarget -> {
            if(lookTarget instanceof EntityTracker tracker) {
                if (tracker.getEntity() instanceof Player) {
                    maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                }
            }
        });
    }
    
    /**
     * 执行战斗逻辑
     */
    private void executeCombat(double distance) {
        lastMeleeExecuteTick = maid.tickCount;
        // 从 Brain 获取当前目标
        LivingEntity target = maid.getTarget();
        if (target == null) {
            //LOGGER.debug("[MaidSpell][Caster] melee execute skipped maid={} tick={} delta={} reason=no target", maid.getId(), maid.tickCount, delta);
            return;
        }
        // 确保目标无敌时间为0，允许法术伤害
        target.invulnerableTime = 0;

        // 执行法术施放
        if (spellBookManager != null) {
            LOGGER.debug("[MaidSpell][Caster] melee casting providers maid={} tick={}", maid.getId(), maid.tickCount);
            spellBookManager.castSpell(maid);
        }

        boolean slashArt = hasSlashArt(maid.getMainHandItem());
        if(slashArt){
            //LOGGER.debug("[MaidSpell][Caster] vanilla melee suppressed by slash art maid={} tick={} target={}", maid.getId(), maid.tickCount, describeTarget(target));
            return;
        }

        if (distance <= MELEE_RANGE+1) {
            maid.doHurtTarget(target);
            maid.swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * 执行战斗逻辑
     */
    private void executeCombatFar() {
        long delta = lastFarExecuteTick < 0 ? -1 : maid.tickCount - lastFarExecuteTick;
        lastFarExecuteTick = maid.tickCount;
        // 从 Brain 获取当前目标
        LivingEntity target = maid.getTarget();
        if (target == null) {
            //LOGGER.debug("[MaidSpell][Caster] far execute skipped maid={} tick={} delta={} reason=no target", maid.getId(), maid.tickCount, delta);
            return;
        }
        //LOGGER.debug("[MaidSpell][Caster] far execute maid={} tick={} delta={} target={}", maid.getId(), maid.tickCount, delta, describeTarget(target));

        // 确保目标无敌时间为0，允许法术伤害
        target.invulnerableTime = 0;

        // 执行法术施放
        if (spellBookManager != null) {
            spellBookManager.castSpell(maid);
        }
    }

    private static String describeTarget(LivingEntity target) {
        if (target == null) {
            return "null";
        }
        return target.getType() + "#" + target.getId() + " alive=" + target.isAlive() + " dying=" + target.isDeadOrDying()
                + " removed=" + target.isRemoved() + " hp=" + target.getHealth() + "/" + target.getMaxHealth();
    }

    /**
     * 检查拔刀剑是否有SA
     * @param itemStack 拔刀剑物品
     * @return 是否有SA
     */
    public static boolean hasSlashArt(ItemStack itemStack) {
        if(!ModList.get().isLoaded("slashblade")) {
            return false;
        }
        
        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof ItemSlashBlade) ) {
            return false;
        }

        // 不能只凭物品类型判断：部分拔刀剑没有可释放的 SA，需要读取 BladeState。
        return itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> state.getSlashArtsKey() != null && !state.getSlashArtsKey().equals(SlashArtsRegistry.NONE.getId())).orElse(false);
    }

}
