package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidBackpackChangeEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTamedEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData;
import com.github.yimeng261.maidspell.spell.manager.AllianceManager;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.EnderPocketMessage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.utils.ChunkLoadingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
     * 如果装备了锚定核心，启用区块加载
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.setMaid(maid);
            manager.initSpellBooks();
            LivingEntity owner = maid.getOwner();
            Global.maidList.add(maid);
            if(owner != null) {
                Global.maidInfos.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(maid.getUUID(), maid);
            }
            addStepHeightToMaid(maid);
            
            // 检查女仆的区块加载状态 - 异步处理避免卡死
            if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                ServerLevel serverLevel = (ServerLevel) maid.level();
                MinecraftServer server = serverLevel.getServer();
                
                // 延迟异步处理区块加载，完全避免在实体加载过程中触发
                UUID maidId = maid.getUUID();

                server.execute(() -> {
                    try {
                        // 重新获取女仆实体，确保引用有效
                        Entity delayedEntity = serverLevel.getEntity(maidId);
                        if (delayedEntity instanceof EntityMaid delayedMaid) {
                            // 使用完全基于SavedData的检查方法
                            if (ChunkLoadingManager.shouldEnableChunkLoading(delayedMaid, server)) {
                                // 从全局SavedData恢复区块加载
                                ChunkLoadingManager.restoreChunkLoadingFromSavedData(delayedMaid, server);
                            } else {
                                // 首次装备，启用区块加载
                                ChunkLoadingManager.enableChunkLoading(delayedMaid);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("处理女仆区块加载时发生错误: {}", e.getMessage(), e);
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onMaidInvPutOn(MaidBackpackChangeEvent.PutOn event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        manager.addSpellItem(maid,event.getItemStack());
    }

    @SubscribeEvent
    public static void onMaidInvTakeOff(MaidBackpackChangeEvent.TakeOff event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        manager.removeSpellItem(maid,event.getItemStack());
        LOGGER.debug("itemstack: {} take off", event.getItemStack());
    }
    
    /**
     * 玩家登录时同步末影腰包数据并恢复女仆区块加载
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for(EntityMaid maid : Global.maidList){
                LivingEntity owner = maid.getOwner();
                if(owner != null) {
                    Global.maidInfos.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(maid.getUUID(), maid);
                }
            }
            
            // 为该玩家拥有的女仆恢复区块加载状态
            restorePlayerMaidChunkLoading(player);
            
            try {
                // 获取玩家的末影腰包女仆数据并推送给客户端
                List<EnderPocketService.EnderPocketMaidInfo> maidInfos = 
                        EnderPocketService.getPlayerEnderPocketMaids(player);
                
                if (!maidInfos.isEmpty()) {
                    LOGGER.debug("[MaidSpell] Pushing ender pocket data to player {} on login: {} maids", 
                                player.getName().getString(), maidInfos.size());
                    EnderPocketMessage message = EnderPocketMessage.serverPushUpdate(maidInfos);
                    NetworkHandler.CHANNEL.sendTo(
                            message, 
                            player.connection.connection,
                            net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
                    );
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
     * 同时禁用区块加载
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
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
    public static void onMaidEquip(LivingEquipmentChangeEvent event) {
        if(event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide()) {
            if (!maid.level().isClientSide()) {
                SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
                LOGGER.debug("[MaidSpell] from: {}, to: {}",event.getFrom(), event.getTo());
                if(event.getTo() != ItemStack.EMPTY){
                    manager.addSpellItem(maid,event.getTo());
                }
                if(event.getFrom() != ItemStack.EMPTY){
                    manager.removeSpellItem(maid,event.getFrom());
                }
            }
        }
    }

    /**
     * 监听实体传送事件
     * 当女仆被传送时，更新其区块加载状态
     */
    @SubscribeEvent
    public static void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide()) {
            try {
                // 检查女仆是否装备了锚定核心
                if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                    UUID maidId = maid.getUUID();
                    Global.LOGGER.debug("检测到女仆 {} 传送事件，准备更新区块加载", maidId);

                    // 预加载目标区块
                    if(maid.level() instanceof ServerLevel level) {
                        ChunkLoadingManager.preloadTeleportTarget(
                                maid,
                                new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ()),
                                level
                        );
                        Global.LOGGER.debug("预加载女仆 {} 传送目标区块", maidId);
                    }

                }
            } catch (Exception e) {
                Global.LOGGER.error("处理女仆传送事件时发生错误: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 监听实体跨维度传送事件
     * 当女仆跨维度传送时，更新其区块加载状态
     */
    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide()) {
            try {
                // 检查女仆是否装备了锚定核心
                if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {

                    UUID maidId = maid.getUUID();
                    Global.LOGGER.debug("女仆 {} 跨维度传送，禁用当前维度区块加载", maidId);
                    
                    // 启用新维度的区块加载
                    MinecraftServer server = maid.getServer();
                    if (server != null) {
                        // 重新获取女仆实体（可能在新维度）
                        Entity newMaid = Objects.requireNonNull(server.getLevel(event.getDimension()))
                            .getEntity(maidId);
                        if (newMaid instanceof EntityMaid newMaidEntity) {
                            ChunkLoadingManager.enableChunkLoading(newMaidEntity);
                            Global.LOGGER.debug("女仆 {} 跨维度传送完成，启用新维度区块加载", maidId);
                        }

                    }
                }
            } catch (Exception e) {
                Global.LOGGER.error("处理女仆跨维度传送事件时发生错误: {}", e.getMessage(), e);
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
                manager.tick();
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
    public static void onEntityHurt(LivingHurtEvent event) {
        Entity entity = event.getEntity();
        Entity source = event.getSource().getEntity();
        if(source instanceof EntityMaid maid){
            processor_pre(event, maid);
        }


        if(entity instanceof EntityMaid maid){
            Global.commonHurtCalc.forEach(function -> function.apply(event, maid));

            Global.baubleCommonHurtCalcPre.forEach((item, func)->{
                if(BaubleStateManager.hasBauble(maid, item)){
                    func.apply(event, maid);
                }
            });
        }
    }
    

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent event) {
        Entity entity = event.getEntity();
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();
        
        if(source instanceof EntityMaid maid){
            processorAft(event, maid);
        }else if(direct instanceof EntityMaid maid){
            processorAft(event, maid);
        }

        if(entity instanceof Player player){
            Global.playerHurtCalcAft.forEach(func-> func.apply(event,player));
        }
    }

    @SubscribeEvent
    public static void onMaidEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            Global.baubleEffectAddedCalc.forEach((item, func)->{
                if(BaubleStateManager.hasBauble(maid,item)){
                    func.apply(event, maid);
                }
            });
        }
    }



    private static void processorAft(LivingDamageEvent event, EntityMaid maid) {
        Global.baubleDamageCalcAft.forEach((item, func) -> {
            if(BaubleStateManager.hasBauble(maid, item)){
                func.apply(event, maid);
            }
        });
    }

    private static void processor_pre(LivingHurtEvent event, EntityMaid maid) {
        Global.commonDamageCalc.forEach(function -> function.apply(event, maid));

        Global.baubleDamageCalcPre.forEach((item, func) -> {
            if(BaubleStateManager.hasBauble(maid, item)){
                func.apply(event, maid);
            }
        });
    }

    /**
     * 当女仆死亡时处理饰品逻辑并清理数据
     */
    @SubscribeEvent
    public static void onMaidDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            // 先处理饰品的死亡事件

            Global.baubleDeathCalc.forEach((item, func)->{
                if(BaubleStateManager.hasBauble(maid,item)){
                    func.apply(event, maid);
                }
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
            for (ISpellBookProvider<?, ?> provider : manager.getProviders()) {
                if (provider.isCasting(maid)) {
                    provider.stopCasting(maid);
                }
            }
            MaidSlashBladeData.remove(maid.getUUID());
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

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        Global.maidList.clear();
        Global.maidInfos.clear();
    }

    /**
     * 为玩家拥有的女仆恢复区块加载状态
     * 在玩家登录时调用，确保远距离的女仆也能恢复区块加载
     * 完全基于全局SavedData，不依赖实体NBT访问
     */
    private static void restorePlayerMaidChunkLoading(ServerPlayer player) {
        try {
            MinecraftServer server = player.getServer();
            if (server == null) return;
            
            // 获取全局区块加载数据
            ChunkLoadingManager.ChunkLoadingData data = ChunkLoadingManager.ChunkLoadingData.get(server);
            Map<UUID, ChunkLoadingManager.ChunkKey> savedPositions = data.getSavedPositions();
            
            if (savedPositions.isEmpty()) {
                return; // 没有需要恢复的数据
            }
            
            int restoredCount = 0;
            int totalCount = savedPositions.size();
            
            LOGGER.info("开始为玩家 {} 恢复 {} 个女仆的区块加载状态", player.getName().getString(), totalCount);
            
            for (Map.Entry<UUID, ChunkLoadingManager.ChunkKey> entry : savedPositions.entrySet()) {
                UUID maidId = entry.getKey();
                ChunkLoadingManager.ChunkKey info = entry.getValue();
                
                try {
                    // 获取对应维度的服务器世界
                    ServerLevel targetLevel = info.level();
                    if (targetLevel == null) {
                        LOGGER.warn("无法找到维度 {} 来恢复女仆 {} 的区块加载", null, maidId);
                        continue;
                    }
                    
                    // 直接基于SavedData恢复区块加载，不需要等待实体加载
                    boolean success = ForgeChunkManager.forceChunk(
                        targetLevel,
                        MaidSpellMod.MOD_ID,
                        maidId,
                        info.chunkPos().x,
                        info.chunkPos().z,
                        true,
                        true
                    );
                    
                    if (success) {
                        // 同时更新内存中的记录
                        ChunkLoadingManager.maidChunkPositions.put(maidId, info);
                        restoredCount++;
                        
                        LOGGER.debug("成功恢复女仆 {} 的区块加载: {} ({})", 
                            maidId, info.chunkPos(), info.level());
                    } else {
                        LOGGER.warn("无法恢复女仆 {} 的区块加载: {} ({})", 
                            maidId, info.chunkPos(), info.level());
                    }
                } catch (Exception e) {
                    LOGGER.warn("恢复女仆 {} 区块加载时发生错误: {}", maidId, e.getMessage());
                }
            }
            
            if (restoredCount > 0) {
                LOGGER.info("为玩家 {} 成功恢复了 {}/{} 个女仆的区块加载", 
                    player.getName().getString(), restoredCount, totalCount);
            } else {
                LOGGER.warn("为玩家 {} 恢复女仆区块加载失败，没有成功恢复任何女仆", 
                    player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("为玩家 {} 恢复女仆区块加载时发生严重错误", player.getName().getString(), e);
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
            @SuppressWarnings("removal")
            var hiddenRetreatStructureSet = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOptional(new ResourceLocation("touhou_little_maid_spell", "hidden_retreat"));

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