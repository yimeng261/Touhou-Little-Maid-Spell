package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidEquipEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

// SlashBlade相关导入
import mods.flammpfeil.slashblade.capability.inputstate.InputStateCapabilityProvider;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * 女仆法术事件处理器
 * 处理女仆生命周期相关的法术管理事件
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class MaidSpellEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 女仆步高属性修饰符的UUID
    private static final UUID MAID_STEP_HEIGHT_UUID = UUID.fromString("8e2c4a16-7f9d-4b45-a3e2-1c8f5d9a6b47");

    /**
     * 当女仆进入世界时，确保有对应的SpellBookManager
     * 同时更新背包处理器的女仆引用（魂符收放后特别重要）
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            // 确保女仆有对应的管理器并更新引用
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.setMaid(maid);
            manager.updateSpellBooks();
            LivingEntity owner = maid.getOwner();
            if(owner != null) {
                Global.maidInfos.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(maid.getUUID(), maid);
            }
            
            // 为女仆添加步高属性，让她能够直接走上一格高的方块
            addStepHeightToMaid(maid);
        }
    }

    /**
     * 当女仆离开世界时，停止所有施法但不移除管理器
     * 因为女仆可能很快就会重新进入世界（魂符移动）
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            // 停止所有正在进行的施法，但不移除管理器
            // 因为女仆可能很快就会重新进入世界（魂符移动）
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.stopAllCasting();
            LivingEntity owner = maid.getOwner();
            if(owner != null) {
                Global.maidInfos.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).remove(maid.getUUID());
            }
        }
    }

    /**
     * 监听女仆装备变化事件
     * 当女仆的主手或副手装备发生变化时，更新法术书管理器
     */
    @SubscribeEvent
    public static void onMaidEquip(MaidEquipEvent event) {
        EntityMaid maid = event.getMaid();
        EquipmentSlot slot = event.getSlot();
        
        // 只处理手部装备变化
        if (!maid.level().isClientSide() && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) {
            try {
                SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
                if (manager != null) {
                    // 更新法术书
                    manager.updateSpellBooks();
                }
            } catch (Exception e) {
                LOGGER.error("Error handling maid equip event for maid {}: {}", 
                    maid.getName().getString(), e.getMessage(), e);
            }
        }
    }

    /**
     * 监听女仆tick事件
     * 在每个tick中处理法术相关逻辑
     */
    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (!maid.level().isClientSide()) {
            try {
                SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
                if (manager != null) {
                    manager.tick();
                }
            } catch (Exception e) {
                LOGGER.error("Error in maid tick handler for maid {}: {}", 
                    maid.getName().getString(), e.getMessage(), e);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityHurt(LivingHurtEvent event) {
        Entity entity = event.getEntity();
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();
        if(source instanceof EntityMaid maid){
            processor_pre(event, maid);
        }else if(direct instanceof EntityMaid maid){
            processor_pre(event, maid);
        }

        if(entity instanceof EntityMaid maid){
            Global.common_hurtProcessors.forEach(function -> function.apply(event, maid));

            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                BiFunction<LivingHurtEvent, EntityMaid, Void> func = Global.bauble_hurtProcessors_pre.getOrDefault(bauble.getDescriptionId(), (livingHurtEvent, entityMaid) -> null);
                func.apply(event, maid);
            });
        }
    }

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent event) {
        Entity entity = event.getEntity();
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();
        if(source instanceof EntityMaid maid){
            processor_aft(event, maid);
        }else if(direct instanceof EntityMaid maid){
            processor_aft(event, maid);
        }

        if(entity instanceof EntityMaid maid){
            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                BiFunction<LivingDamageEvent, EntityMaid, Void> func = Global.bauble_hurtProcessors_aft.getOrDefault(bauble.getDescriptionId(), (livingHurtEvent, entityMaid) -> null);
                func.apply(event, maid);
            });
        }else if(entity instanceof Player player){
            Global.player_hurtProcessors_aft.forEach(func-> func.apply(event,player));
        }
    }

    @SubscribeEvent
    public static void onMaidEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                BiFunction<MobEffectEvent.Added, EntityMaid, Void> func = Global.bauble_effectAddedProcessors.getOrDefault(bauble.getDescriptionId(), (mobEffectEvent, entityMaid) -> null);
                func.apply(event, maid);
            });
        }
    }



    private static void processor_aft(LivingDamageEvent event, EntityMaid maid) {
        BaubleStateManager.getBaubles(maid).forEach(bauble->{
            BiFunction<LivingDamageEvent, EntityMaid, Void> func = Global.bauble_damageProcessors_aft.getOrDefault(bauble.getDescriptionId(), (livingHurtEvent, entityMaid) -> null);
            func.apply(event, maid);
        });
    }

    private static void processor_pre(LivingHurtEvent event, EntityMaid maid) {
        Global.common_damageProcessors.forEach(function -> function.apply(event, maid));

        BaubleStateManager.getBaubles(maid).forEach(bauble->{
            BiFunction<LivingHurtEvent, EntityMaid, Void> func = Global.bauble_damageProcessors_pre.getOrDefault(bauble.getDescriptionId(), (livingHurtEvent, entityMaid) -> null);
            func.apply(event, maid);
        });
    }

    /**
     * 为女仆附加INPUT_STATE能力，确保拔刀剑能正常工作
     * 使用不同的键名以避免与拔刀剑模组的能力冲突
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        // 只在服务器端处理，避免客户端渲染问题
        if (event.getObject().level().isClientSide()) {
            return;
        }
        
        if (event.getObject() instanceof EntityMaid) {
            // 检查拔刀剑模组是否加载
            if (!net.minecraftforge.fml.ModList.get().isLoaded("slashblade")) {
                return;
            }
            
            Entity entity = event.getObject();
            
            // 使用我们自己的键名，以防拔刀剑的能力没有正确附加
            ResourceLocation maidInputStateKey = new ResourceLocation(MaidSpellMod.MOD_ID, "maid_inputstate");
            
            try {
                // 检查是否已经有这个能力，避免重复添加
                if (!entity.getCapability(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).isPresent()) {
                    event.addCapability(maidInputStateKey, new InputStateCapabilityProvider());
                    LOGGER.debug("Added INPUT_STATE capability to maid entity");
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to add INPUT_STATE capability to maid: {}", e.getMessage());
                // 不要重新抛出异常，避免影响其他模组
            }
        }
    }

    /**
     * 当女仆死亡时清理数据
     */
    @SubscribeEvent
    public static void onMaidDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            // 清理女仆的法术数据
            cleanupMaidSpellData(maid);
        }
    }



    /**
     * 清理女仆的法术相关数据
     */
    private static void cleanupMaidSpellData(EntityMaid maid) {
        try {
            // 通过SpellBookManager获取提供者并停止所有正在进行的施法
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            if (manager != null) {
                for (ISpellBookProvider provider : manager.getProviders()) {
                    if (provider.isCasting(maid)) {
                        provider.stopCasting(maid);
                    }
                }
            }
            
            // 清理特定模组的数据
            com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData.remove(maid.getUUID());
            
            // 移除女仆的管理器
            SpellBookManager.removeManager(maid);
            
        } catch (Exception e) {
            // 静默处理清理错误，避免影响游戏正常运行
        }
    }
    
    /**
     * 为女仆添加步高属性，让她能够直接走上一格高的方块
     */
    private static void addStepHeightToMaid(EntityMaid maid) {
        try {
            // 获取步高属性实例，进行空检查
            var stepHeightAttribute = maid.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
            if (stepHeightAttribute == null) {
                LOGGER.warn("Maid {} does not have step height attribute", maid.getName().getString());
                return;
            }
            
            // 检查女仆是否已经有步高属性修饰符，避免重复添加
            if (stepHeightAttribute.getModifier(MAID_STEP_HEIGHT_UUID) == null) {
                // 添加1.0的步高增加，让女仆能走上一格高的方块
                AttributeModifier stepHeightModifier = new AttributeModifier(
                    MAID_STEP_HEIGHT_UUID, 
                    "Maid Step Height Addition", 
                    1.0, 
                    AttributeModifier.Operation.ADDITION
                );
                
                stepHeightAttribute.addPermanentModifier(stepHeightModifier);
                LOGGER.debug("Added step height attribute to maid: {}", maid.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to add step height attribute to maid {}: {}", 
                maid.getName().getString(), e.getMessage());
        }
    }
} 