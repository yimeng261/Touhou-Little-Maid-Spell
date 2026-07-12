package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidBackpackChangeEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTamedEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.spell.providers.PsiProvider;
import com.github.yimeng261.maidspell.spell.manager.AllianceManager;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatDimensionData;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketBauble;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.block.entity.SuppressionStoneBlockEntity;
import com.github.yimeng261.maidspell.utils.ChunkLoadingManager;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import com.github.yimeng261.maidspell.utils.MaidReviveEffectCleanup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

import java.util.*;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.github.yimeng261.maidspell.MaidSpellMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

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

            if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                ChunkLoadingManager.getCurrentServer().execute(()->{
                    ChunkLoadingManager.enableChunkLoading(maid);
                });
            }

            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.onMaidJoin(maid);

            Global.updateMaidInfo(maid,true);

            addStepHeightToMaid(maid);
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
     * 同时检查玩家是否应该在隐世之境维度中
     * 检查并发送节日祝福
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                // 获取玩家的末影腰包女仆数据并推送给客户端
                EnderPocketBauble.pushEnderPocketDataToClient(player);
            } catch (Exception e) {
                LOGGER.error("[MaidSpell] Failed to sync ender pocket data for player {} on login: {}",
                            player.getName().getString(), e.getMessage(), e);
            }

            // 检查并发送节日祝福
            try {
                FestivalGreetingManager.checkAndSendGreeting(player);
            } catch (Exception e) {
                LOGGER.error("[MaidSpell] Failed to check/send festival greeting for player {} on login: {}",
                            player.getName().getString(), e.getMessage(), e);
            }

            try {
                restoreRetreatLocationIfNeeded(player);
            } catch (Exception e) {
                LOGGER.error("[MaidSpell] Failed to restore retreat location for player {} on login: {}",
                    player.getName().getString(), e.getMessage(), e);
            }
        }
    }

    /**
     * 玩家断线时清理结构搜索队列，防止幽灵请求阻塞其他玩家
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }

            RetreatDimensionData data = RetreatDimensionData.get(server);
            if (TheRetreatDimension.isInRetreat(player)) {
                data.setPendingRestore(player.getUUID(), player.level().dimension(), player.blockPosition());
            } else {
                data.clearPendingRestore(player.getUUID());
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
            cleanupMaidBaubleRuntimeState(maid.getUUID());

            if (MaidHardRemovalProtection.handleMaidLeaveLevel(maid)) {
                manager.releaseMaidRuntimeReferences(maid);
                Global.updateMaidInfo(maid,true);
                return;
            }

            MinecraftServer server = event.getLevel().getServer();
            if (server != null) {
                manager.onMaidLeave(maid, server);
            } else {
                manager.releaseMaidRuntimeReferences(maid);
            }

            if (shouldReleaseMaidChunkLoading(maid.getRemovalReason())) {
                MaidHardRemovalProtection.allowClientRemoval(maid);
                ChunkLoadingManager.disableChunkLoading(maid);
            }

            // 从全局女仆列表中移除，避免内存泄漏
            Global.updateMaidInfo(maid,false);
        }
    }

    /**
     * 监听女仆装备变化事件
     * 当女仆的主手或副手装备发生变化时，更新法术书管理器
     */
    @SubscribeEvent
    public static void onMaidEquip(LivingEquipmentChangeEvent event) {
        if(event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide()) {
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            LOGGER.debug("[MaidSpell] from: {}, to: {}",event.getFrom(), event.getTo());
            if(!event.getFrom().isEmpty()){
                manager.removeSpellItem(maid,event.getFrom());
            }
            if(!event.getTo().isEmpty()){
                manager.addSpellItem(maid,event.getTo());
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
        if (event.getEntity() instanceof ServerPlayer player
            && TheRetreatDimension.isInRetreat(player)
            && !TheRetreatDimension.isRetreatDimension(event.getDimension().location())) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                RetreatDimensionData.get(server).clearPendingRestore(player.getUUID());
            }
        }

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
            if(maid.tickCount%2==0){
                if(BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                    MaidHardRemovalProtection.rememberProtected(maid);
                    ChunkLoadingManager.enableChunkLoading(maid);
                }
            }

            try {
                SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
                manager.tick(maid);
                // 每20个tick更新一次全局女仆状态
                if(maid.tickCount%20 == 0){
                    boolean isMaidSpellTask = "maidspell".equals(maid.getTask().getUid().getNamespace());

                    if(maid.isNoAi() && isMaidSpellTask){
                        maid.setNoAi(false);
                    }

                    Global.updateMaidInfo(maid,true);
                }
            } catch (Exception e) {
                LOGGER.error("Error in maid tick handler for maid {}: {}",
                    maid.getName().getString(), e.getMessage(), e);
            }
        }
    }

    /**
     * 镇石：阻止周围区块内的敌对生物自然生成
     */
    @SubscribeEvent
    public static void onMobPositionCheck(MobSpawnEvent.PositionCheck event) {
        // 只拦截自然生成类的生成方式
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType == MobSpawnType.BREEDING
            || spawnType == MobSpawnType.MOB_SUMMONED
            || spawnType == MobSpawnType.CONVERSION
            || spawnType == MobSpawnType.BUCKET
            || spawnType == MobSpawnType.SPAWN_EGG
            || spawnType == MobSpawnType.COMMAND
            || spawnType == MobSpawnType.DISPENSER
            || spawnType == MobSpawnType.SPAWNER) {
            return;
        }

        // 只阻止敌对生物
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }

        // 检查是否在镇石压制范围内
        BlockPos spawnPos = new BlockPos((int) event.getX(), (int) event.getY(), (int) event.getZ());
        if (SuppressionStoneBlockEntity.isWithinSuppressionRange(event.getLevel(), spawnPos)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onMobFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!TheRetreatDimension.isRetreatDimension(level.dimension().location())) {
            return;
        }
        if (!isControlledRetreatSpawnType(event.getSpawnType())) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (!Config.allowMobSpawnsInRetreat) {
            event.setSpawnCancelled(true);
            return;
        }

        if (!Config.allowHostileMobSpawnsInRetreat && entity instanceof Enemy) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityHurt(LivingHurtEvent event) {
        if (shouldBlockMaidPsiPlayerDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            LOGGER.debug("[MaidSpell/Psi] blocked player hurt target={} source={} direct={} amount={}",
                    event.getEntity().getUUID(), describeDamageEntity(event.getSource().getEntity()),
                    describeDamageEntity(event.getSource().getDirectEntity()), event.getAmount());
            return;
        }

        Entity entity = event.getEntity();
        Entity source = event.getSource().getEntity();
        if(source instanceof EntityMaid maid){
            processor_pre(event, maid);
        }


        if(entity instanceof EntityMaid maid){
            Global.commonHurtCalc.forEach(function -> function.apply(event, maid));

            Global.baubleHurtEventHandlers.forEach((item, func)->{
                if(BaubleStateManager.hasBauble(maid, item)){
                    func.apply(event, maid);
                }
            });
        }
    }


    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent event) {
        if (shouldBlockMaidPsiPlayerDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            LOGGER.debug("[MaidSpell/Psi] blocked player damage target={} source={} direct={} amount={}",
                    event.getEntity().getUUID(), describeDamageEntity(event.getSource().getEntity()),
                    describeDamageEntity(event.getSource().getDirectEntity()), event.getAmount());
            return;
        }

        Entity entity = event.getEntity();
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();

        if(source instanceof EntityMaid maid){
            processorAft(event, maid);
        }else if(direct instanceof EntityMaid maid){
            processorAft(event, maid);
        }

        if(entity instanceof Player player){
            Global.playerDamageHandlers.forEach(func-> func.apply(event,player));
        }
    }


    private static final boolean PSI_LOADED = ModList.get().isLoaded("psi");

    private static boolean shouldBlockMaidPsiPlayerDamage(LivingEntity target, DamageSource source) {
        if (!PSI_LOADED || !(target instanceof Player)) {
            return false;
        }
        Entity causing = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (isMaidPsiFakeCaster(causing) || isMaidPsiFakeCaster(direct)) {
            return true;
        }
        if (direct != null && isPsiSpellEntity(direct)) {
            return true;
        }
        Vec3 sourcePos = source.getSourcePosition();
        return sourcePos != null && isNearActiveMaidPsiSpell(target.level(), sourcePos);
    }

    private static boolean isMaidPsiFakeCaster(Entity entity) {
        if (!(entity instanceof FakePlayer fakePlayer)) {
            return false;
        }
        return PsiProvider.getMaidUuidFromCaster(fakePlayer) != null;
    }

    private static boolean isPsiSpellEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        String type = entity.getType().builtInRegistryHolder().key().location().toString();
        return type.startsWith("psi:") && type.contains("spell");
    }

    private static boolean isNearActiveMaidPsiSpell(Level level, Vec3 pos) {
        return !level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(pos, pos).inflate(3.0D),
                entity -> isMaidPsiFakeCaster(entity) || isPsiSpellEntity(entity)).isEmpty();
    }

    private static String describeDamageEntity(Entity entity) {
        if (entity == null) {
            return "null";
        }
        return entity.getType().builtInRegistryHolder().key().location() + ":" + entity.getUUID();
    }


    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity exploder = event.getExplosion().getExploder();
        boolean maidPsiExplosion = isMaidPsiFakeCaster(exploder)
                || isPsiSpellEntity(exploder)
                || isMaidPsiFakeCaster(event.getExplosion().getIndirectSourceEntity());
        if (!maidPsiExplosion) {
            return;
        }
        int before = event.getAffectedEntities().size();
        event.getAffectedEntities().removeIf(Player.class::isInstance);
        event.getExplosion().getHitPlayers().clear();
        int removed = before - event.getAffectedEntities().size();
        if (removed > 0) {
            LOGGER.debug("[MaidSpell/Psi] removed {} players from explosion knockback source={}",
                    removed, describeDamageEntity(exploder));
        }
    }

    @SubscribeEvent
    public static void onMaidEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            Global.baubleEffectAddedHandlers.forEach((item, func)->{
                if(BaubleStateManager.hasBauble(maid,item)){
                    func.apply(event, maid);
                }
            });
        }
    }

    private static void processorAft(LivingDamageEvent event, EntityMaid maid) {
        Global.baubleDamageHandlers.forEach((item, func) -> {
            if(BaubleStateManager.hasBauble(maid, item)){
                func.apply(event, maid);
            }
        });
    }

    private static void processor_pre(LivingHurtEvent event, EntityMaid maid) {
        Global.commonHurtHandlers.forEach(function -> function.apply(event, maid));

        Global.baubleHurtHandlers.forEach((item, func) -> {
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
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide()) {
            // 先处理饰品的死亡事件

            Global.baubleDeathHandlers.forEach((item, func)->{
                if(BaubleStateManager.hasBauble(maid,item)){
                    func.apply(event, maid);
                }
            });

            // 如果事件未被取消，则清理女仆的法术数据
            if (!event.isCanceled()) {
                SpellBookManager.getOrCreateManager(maid).removeMaidData(maid);
                // 从全局女仆列表中移除，避免内存泄漏
                Global.updateMaidInfo(maid,false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMaidNormalDeathCleanup(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide()) {
            MaidReviveEffectCleanup.cleanupBeforeNormalDeath(maid);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMaidBaubleRuntimeCleanup(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide()) {
            cleanupMaidBaubleRuntimeState(maid.getUUID());
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

            // 推送末影腰包数据更新
            Global.updateMaidInfo(maid,true);
            EnderPocketBauble.pushEnderPocketDataToClient((ServerPlayer) player);
        }
    }

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        clearBaubleRuntimeState();
        Global.activeMaids.clear();
        Global.ownerMaidRegistry.clear();
        MaidHardRemovalProtection.clear();
        SpellBookManager.clearAll();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        AllianceManager.cleanupLegacyTeams(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearBaubleRuntimeState();
        SpellBookManager.clearAll();
    }

    private static void cleanupMaidBaubleRuntimeState(UUID maidId) {
        SoulBookBauble.cleanupMaid(maidId);
        WoundRimeBladeBauble.cleanupMaid(maidId);
        SilverCercisBauble.cleanupMaid(maidId);
    }

    private static void clearBaubleRuntimeState() {
        SoulBookBauble.clearSession();
        WoundRimeBladeBauble.clearSession();
        SilverCercisBauble.clearSession();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MaidHardRemovalProtection.tick(event.getServer());
            SpellBookManager.tickPendingRemovals(event.getServer());
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

    private static boolean isControlledRetreatSpawnType(MobSpawnType spawnType) {
        return switch (spawnType) {
            case NATURAL,
                 CHUNK_GENERATION,
                 SPAWNER,
                 STRUCTURE,
                 JOCKEY,
                 EVENT,
                 REINFORCEMENT,
                 TRIGGERED,
                 PATROL -> true;
            case BREEDING,
                 MOB_SUMMONED,
                 CONVERSION,
                 BUCKET,
                 SPAWN_EGG,
                 COMMAND,
                 DISPENSER -> false;
        };
    }

    private static boolean shouldReleaseMaidChunkLoading(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
            || reason == Entity.RemovalReason.CHANGED_DIMENSION
            || reason == Entity.RemovalReason.KILLED
            || reason == Entity.RemovalReason.DISCARDED;
    }

    private static void restoreRetreatLocationIfNeeded(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        RetreatDimensionData data = RetreatDimensionData.get(server);
        RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(player.getUUID());
        if (info == null || info.pendingRestorePos == null) {
            return;
        }

        ResourceKey<Level> targetDimension = info.getPendingRestoreDimensionKey();
        if (targetDimension == null || !TheRetreatDimension.isRetreatDimension(targetDimension.location())) {
            data.clearPendingRestore(player.getUUID());
            return;
        }

        if (player.level().dimension().equals(targetDimension)) {
            return;
        }

        BlockPos targetPos = info.pendingRestorePos;
        PlayerRetreatManager.getOrCreateRetreatByKeyAsync(server, targetDimension, player.getUUID())
            .whenComplete((targetLevel, throwable) -> server.execute(() -> {
                ServerPlayer currentPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (currentPlayer == null) {
                    return;
                }

                if (throwable != null || targetLevel == null) {
                    LOGGER.error("[MaidSpell] Failed to prepare retreat dimension {} for player {}",
                        targetDimension.location(), player.getName().getString(), throwable);
                    return;
                }

                if (currentPlayer.level().dimension().equals(targetDimension)) {
                    return;
                }

                TheRetreatDimension.teleportToRetreat(currentPlayer, targetLevel, targetPos);
            }));
    }

}
