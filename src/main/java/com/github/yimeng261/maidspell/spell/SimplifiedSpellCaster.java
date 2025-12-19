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
 */
public class SimplifiedSpellCaster {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogUtils.getLogger();
    private final EntityMaid maid;
    private LivingEntity target;
    
    public static double MELEE_RANGE;
    public static double FAR_RANGE;

    private final SpellBookManager spellBookManager;

    public SimplifiedSpellCaster(EntityMaid maid) {
        this.maid = maid;
        this.spellBookManager = SpellBookManager.getOrCreateManager(maid);
    }
    
    /**
     * 设置当前攻击目标
     * @param target 攻击目标
     */
    public void setTarget(LivingEntity target) {
        this.target = target;
        // 同时设置给SpellBookManager
        if (spellBookManager != null) {
            for (ISpellBookProvider<?, ?> provider : spellBookManager.getProviders()) {
                provider.setTarget(maid,target);
            }
        }
    }
    
    /**
     * 检查是否有有效目标
     */
    public boolean hasValidTarget() {
        return target != null && target.isAlive();
    }
    
    
    /**
     * 执行施法逻辑
     */
    public void melee_tick() {

        if (!hasValidTarget()) {
            return; // 没有有效目标，退出
        }
        
        if (maid.tickCount % Config.meleeAttackInterval == 0) {
            // 执行战斗逻辑
            clearLookTarget(maid);
            double distance = maid.distanceTo(target);
            executeCombat(distance);
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
        // 确保目标无敌时间为0，允许法术伤害
        if (target == null) {
            return;
        }
        target.invulnerableTime = 0;

        // 执行法术施放
        if (spellBookManager != null) {
            spellBookManager.castSpell(maid);
        }

        if(hasSlashArt(maid.getMainHandItem())){
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
        // 确保目标无敌时间为0，允许法术伤害
        if (target == null) {
            return;
        }
        target.invulnerableTime = 0;

        // 执行法术施放
        if (spellBookManager != null) {
            spellBookManager.castSpell(maid);
        }
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

        if(itemStack.getItem() instanceof ItemSlashBlade) {
            return true;
        }
        
        return itemStack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> state.getSlashArtsKey() != null && !state.getSlashArtsKey().equals(SlashArtsRegistry.NONE.getId())).orElse(false);
    }

}