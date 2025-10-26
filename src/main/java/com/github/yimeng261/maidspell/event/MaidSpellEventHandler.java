package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidEquipEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTamedEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketPushUpdate;
import com.github.yimeng261.maidspell.spell.manager.AllianceManager;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 女仆法术事件处理器
 * 处理女仆生命周期相关的法术管理事件
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class MaidSpellEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 女仆步高属性修饰符的UUID
    private static final ResourceLocation MAID_STEP_HEIGHT_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid_step_height");

    /**
     * 当女仆进入世界时，确保有对应的SpellBookManager
     * 同时更新背包处理器的女仆引用（魂符收放后特别重要）
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.setMaid(maid);
            manager.updateSpellBooks();
            LivingEntity owner = maid.getOwner();
            Global.maidList.add(maid);
            if(owner != null) {
                Global.maidInfos.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(maid.getUUID(), maid);
            }
            addStepHeightToMaid(maid);
            BaubleStateManager.updateAndCheckBaubleState(maid);
        }
    }

    /**
     * 玩家登录时同步末影腰包数据
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for(EntityMaid maid : Global.maidList){
                LivingEntity owner = maid.getOwner();
                if(owner != null) {
                    Global.maidInfos.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(maid.getUUID(), maid);
                }
            }
            try {
                // 获取玩家的末影腰包女仆数据并推送给客户端
                List<EnderPocketService.EnderPocketMaidInfo> maidInfos =
                        EnderPocketService.getPlayerEnderPocketMaids(player);

                if (!maidInfos.isEmpty()) {
                    LOGGER.debug("[MaidSpell] Pushing ender pocket data to player {} on login: {} maids",
                                player.getName().getString(), maidInfos.size());
                    player.connection.send(new S2CEnderPocketPushUpdate(maidInfos, true));
                    LOGGER.debug("[MaidSpell] Pushed ender pocket data to player {} on login: {} maids",
                                player.getName().getString(), maidInfos.size());
                }
            } catch (Exception e) {
                LOGGER.error("[MaidSpell] Failed to sync ender pocket data for player {} on login: {}",
                            player.getName().getString(), e.getMessage(), e);
            }
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
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.stopAllCasting();
            BaubleStateManager.removeMaidBaubles(maid);
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
                // 每20个tick更新一次结盟状态
                if(maid.tickCount%20 == 0){
                    if(maid.isNoAi() && maid.getTask().getUid().toString().startsWith("maidspell")){
                        maid.setNoAi(false);
                    }
                    if(maid.getTask().getUid().toString().startsWith("maidspell")) {
                        if(!AllianceManager.getAllianceStatus().containsKey(maid.getUUID())) {
                            AllianceManager.setMaidAlliance(maid, true);
                        }
                    }else{
                        AllianceManager.setMaidAlliance(maid, false);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error in maid tick handler for maid {}: {}",
                    maid.getName().getString(), e.getMessage(), e);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityHurt(LivingIncomingDamageEvent event) {
        Entity entity = event.getEntity();
        Entity source = event.getSource().getEntity();
        if(source instanceof EntityMaid maid){
            processor_pre(event, maid);
        }

        if(entity instanceof EntityMaid maid){
            Global.common_hurtProcessors.forEach(function -> function.apply(event, maid));

            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                BiFunction<LivingIncomingDamageEvent, EntityMaid, Void> func = Global.bauble_commonHurtProcessors_pre.getOrDefault(bauble.getDescriptionId(), (livingHurtEvent, entityMaid) -> null);
                func.apply(event, maid);
            });
        }

        if(entity instanceof Player player){
            Global.player_hurtProcessors_pre.forEach(func-> func.apply(event,player));
        }
    }


    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent.Post event) {
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();
        if(source instanceof EntityMaid maid){
            processor_aft(event, maid);
        }else if(direct instanceof EntityMaid maid){
            processor_aft(event, maid);
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



    private static void processor_aft(LivingDamageEvent.Post event, EntityMaid maid) {
        BaubleStateManager.getBaubles(maid).forEach(bauble->{
            BiFunction<LivingDamageEvent.Post, EntityMaid, Void> func = Global.bauble_damageProcessors_aft.getOrDefault(bauble.getDescriptionId(), (livingHurtEvent, entityMaid) -> null);
            func.apply(event, maid);
        });
    }

    private static void processor_pre(LivingIncomingDamageEvent event, EntityMaid maid) {
        Global.common_damageProcessors.forEach(function -> function.apply(event, maid));

        BaubleStateManager.getBaubles(maid).forEach(bauble->{
            BiFunction<LivingIncomingDamageEvent, EntityMaid, Void> func = Global.bauble_damageProcessors_pre.getOrDefault(bauble.getDescriptionId(), (livingHurtEvent, entityMaid) -> null);
            func.apply(event, maid);
        });
    }

    /**
     * 当女仆死亡时处理饰品逻辑并清理数据
     */
    @SubscribeEvent
    public static void onMaidDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            // 先处理饰品的死亡事件
            BaubleStateManager.getBaubles(maid).forEach(bauble->{
                BiFunction<LivingDeathEvent, EntityMaid, Void> func = Global.bauble_deathProcessors.getOrDefault(bauble.getDescriptionId(), (livingDeathEvent, entityMaid) -> null);
                func.apply(event, maid);
            });

            // 如果事件未被取消，则清理女仆的法术数据
            if (!event.isCanceled()) {
                cleanupMaidSpellData(maid);
            }
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
            BaubleStateManager.removeMaidBaubles(maid);
            // MaidSlashBladeData.remove(maid.getUUID());
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
            var stepHeightAttribute = maid.getAttribute(Attributes.STEP_HEIGHT);
            if (stepHeightAttribute == null) {
                LOGGER.warn("Maid {} does not have step height attribute", maid.getName().getString());
                return;
            }

            // 检查女仆是否已经有步高属性修饰符，避免重复添加
            if (stepHeightAttribute.getModifier(MAID_STEP_HEIGHT_ID) == null) {
                // 添加1.0的步高增加，让女仆能走上一格高的方块
                AttributeModifier stepHeightModifier = new AttributeModifier(
                        MAID_STEP_HEIGHT_ID,
                    1.0,
                    AttributeModifier.Operation.ADD_VALUE
                );

                stepHeightAttribute.addPermanentModifier(stepHeightModifier);
                LOGGER.debug("Added step height attribute to maid: {}", maid.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to add step height attribute to maid {}: {}",
                maid.getName().getString(), e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onMaidTamed(MaidTamedEvent event) {
        EntityMaid maid = event.getMaid();
        Player player = event.getPlayer();

        if (!player.level().isClientSide() && player.level() instanceof ServerLevel level) {
            if(maid.isOrderedToSit()&&!maid.isStructureSpawn()&&isInHiddenRetreatStructure(level, maid.blockPosition())){
                player.sendSystemMessage(Component.translatable("item.touhou_little_maid_spell.maid_tamed_event.maid_in_hidden_retreat").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    /**
     * 检查指定位置是否在hidden_retreat结构中
     * @param level 维度
     * @param pos 检查的位置
     * @return 如果在hidden_retreat结构中返回true
     */
    private static boolean isInHiddenRetreatStructure(ServerLevel level, BlockPos pos) {
        try {
            // 检查当前位置是否在hidden_retreat结构中
            // 使用结构管理器检查
            var structureManager = level.structureManager();
            var hiddenRetreatStructureSet = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("touhou_little_maid_spell", "hidden_retreat"));

            if (hiddenRetreatStructureSet.isPresent()) {
                // 检查此位置是否在hidden_retreat结构的范围内
                var structureStart = structureManager.getStructureWithPieceAt(pos, hiddenRetreatStructureSet.get());
                return structureStart.isValid();
            }
        } catch (Exception e) {
            LogUtils.getLogger().debug("Error checking hidden_retreat structure at {}: {}", pos, e.getMessage());
        }
        return false;
    }
}