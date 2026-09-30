package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidBackpackChangeEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTamedEvent;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginData;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import com.github.yimeng261.maidspell.block.entity.SuppressionStoneBlockEntity;
import com.github.yimeng261.maidspell.block.entity.YueLinglanBlockEntity;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatDimensionData;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketBauble;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketPushUpdate;
import com.github.yimeng261.maidspell.player.ChunkLoadingData;
import com.github.yimeng261.maidspell.spell.manager.AllianceManager;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.spell.providers.PsiProvider;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import com.github.yimeng261.maidspell.utils.MaidSuppressionZone;
import com.github.yimeng261.maidspell.utils.PersistentEntityLifecycleGuard;
import com.github.yimeng261.maidspell.utils.MaidReviveEffectCleanup;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.*;

/**
 * 女仆法术事件处理器
 * 处理女仆生命周期相关的法术管理事件
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class MaidSpellEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 锚定女仆所在区块的临时加载票据，女仆 tick 起来后由锚定核心接手强加载。 */
    private static final TicketType<ChunkPos> MAID_ANCHOR_TICKET =
            TicketType.create("maid_anchor", Comparator.comparingLong(ChunkPos::toLong), 300);

    // 女仆步高属性修饰符的UUID
    private static final ResourceLocation MAID_STEP_HEIGHT_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid_step_height");

    /**
     * 当女仆进入世界时，确保有对应的SpellBookManager
     * 同时更新背包处理器的女仆引用（魂符收放后特别重要）
     * 如果装备了锚定核心，启用区块加载
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel() instanceof ServerLevel serverLevel
                && PersistentEntityLifecycleGuard.conflictsWithLoadedEncounter(entity, serverLevel)) {
            LOGGER.warn("Rejecting duplicate loaded encounter entity {} in {}", entity.getUUID(), serverLevel.dimension().location());
            event.setCanceled(true);
            return;
        }
        if (entity instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            MaidOriginData.upgradeStarWitchSpellBooks(maid);

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
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (EntityMaid maid : Global.activeMaids) {
                LivingEntity owner = maid.getOwner();
                if(owner != null) {
                    Global.getOrCreatePlayerMaidMap(owner.getUUID()).put(maid.getUUID(), maid);
                }
            }

            // 注释掉手动维度位置检查，让游戏自动处理
            // checkAndFixPlayerDimension(player);

            // 为该玩家拥有的女仆恢复区块加载状态
            restorePlayerMaidChunkLoading(player);

            try {
                // 始终推送一次，避免客户端保留旧列表。
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

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EnderPocketService.clearRemoteSession(player);
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

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EnderPocketService.clearRemoteSession(player);
        }
    }

    /**
     * 处理玩家重生事件 - 简化版本，主要用于日志记录
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 简单记录玩家重生信息
            if (TheRetreatDimension.isInRetreat(player)) {
                LOGGER.info("Player {} respawned in retreat dimension", player.getName().getString());
            }
        }
    }

    /**
     * 当女仆离开世界时释放运行时法术状态和区块加载；重新加入时会重建这些状态。
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
                AnchorCoreBauble.disableChunkLoading(maid);
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
            if (!event.getFrom().isEmpty()) {
                manager.removeSpellItem(maid,event.getFrom());
            }
            if (!event.getTo().isEmpty()) {
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
                AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
                // 检查女仆是否装备了锚定核心
                if (anchoredEntityMaid.maidSpell$isAnchored()) {
                    UUID maidId = maid.getUUID();
                    Global.LOGGER.debug("检测到女仆 {} 传送事件，准备更新区块加载", maidId);

                    // 预加载目标区块
                    if(maid.level() instanceof ServerLevel level) {
                        ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(event.getTargetX()), SectionPos.blockToSectionCoord(event.getTargetZ()));
                        level.getChunkSource().addRegionTicket(MAID_ANCHOR_TICKET, chunkPos, 3, chunkPos);
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
                AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
                // 检查女仆是否装备了锚定核心
                if (anchoredEntityMaid.maidSpell$isAnchored()) {

                    UUID maidId = maid.getUUID();
                    Global.LOGGER.debug("女仆 {} 跨维度传送，禁用当前维度区块加载", maidId);

                    // 启用新维度的区块加载
                    MinecraftServer server = maid.getServer();
                    if (server != null) {
                        // 重新获取女仆实体（可能在新维度）
                        Entity newMaid = Objects.requireNonNull(server.getLevel(event.getDimension()))
                                .getEntity(maidId);
                        if (newMaid instanceof EntityMaid newMaidEntity) {
                            AnchoredEntityMaid newAnchoredMaid = (AnchoredEntityMaid) newMaidEntity;
                            ChunkPos chunkPos = newMaidEntity.chunkPosition();
                            ServerLevel targetLevel = Objects.requireNonNull(server.getLevel(event.getDimension()));
                            targetLevel.getChunkSource().addRegionTicket(MAID_ANCHOR_TICKET, chunkPos, 3, chunkPos);
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
                manager.tick(maid);
                if (maid.tickCount % 2 == 0) {
                    if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                        MaidHardRemovalProtection.rememberProtected(maid);
                    }
                }
                if(maid.tickCount%20 == 0){
                    boolean isMaidSpellTask = MaidSpellMod.MOD_ID.equals(maid.getTask().getUid().getNamespace());
                    if(maid.isNoAi() && isMaidSpellTask){
                        maid.setNoAi(false);
                    }
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
        BlockPos spawnPos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (SuppressionStoneBlockEntity.isWithinSuppressionRange(event.getLevel(), spawnPos)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void onMobFinalizeSpawn(FinalizeSpawnEvent event) {
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
    public static void onEntityHurt(LivingIncomingDamageEvent event) {
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
            processorPre(event, maid);
        }

        if(entity instanceof EntityMaid maid){
            Global.commonHurtCalc.forEach(function -> function.apply(event, maid));

            Global.baubleHurtEventHandlers.forEach((item, func) -> {
                if(BaubleStateManager.hasBauble(maid, item)){
                    func.apply(event, maid);
                }
            });
        }

        if(entity instanceof Player player){
            Global.playerDamageHandlers.forEach(func -> func.apply(event, player));
        }
    }


    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent.Post event) {
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();
        if(source instanceof EntityMaid maid){
            processorAft(event, maid);
        }else if(direct instanceof EntityMaid maid){
            processorAft(event, maid);
        }
    }

    /**
     * 阻止由女仆 Psi 假玩家或 Psi 法术实体造成的玩家伤害
     */
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
        return !level.getEntities((Entity) null, new AABB(pos, pos).inflate(3.0D),
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
        Entity exploder = event.getExplosion().getDirectSourceEntity();
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
            Global.baubleEffectAddedHandlers.forEach((item, func) -> {
                if(BaubleStateManager.hasBauble(maid,item)){
                    func.apply(event, maid);
                }
            });
        }
    }



    /**
     * 压制区里的女仆饰品效果不生效。
     *
     * <p>在饰品分发口统一拦截，新增饰品无需逐个判断。
     */
    private static boolean suppressed(EntityMaid maid) {
        return MaidSuppressionZone.suppresses(maid);
    }

    private static void processorAft(LivingDamageEvent.Post event, EntityMaid maid) {
        if (suppressed(maid)) {
            return;
        }
        Global.baubleDamageHandlers.forEach((item, func) -> {
            if(BaubleStateManager.hasBauble(maid, item)){
                func.apply(event, maid);
            }
        });
    }

    private static void processorPre(LivingIncomingDamageEvent event, EntityMaid maid) {
        if (suppressed(maid)) {
            return;
        }
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

            Global.baubleDeathHandlers.forEach((item, func) -> {
                if(BaubleStateManager.hasBauble(maid,item)){
                    func.apply(event, maid);
                }
            });

        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMaidNormalDeathCleanup(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        if (event.isCanceled()) {
            // 被复活取消的死亡：魂之书、破愈咒锋、紫荆银冠的运行态保持不变
            MaidReviveEffectCleanup.cleanupAfterCanceledDeath(maid);
            return;
        }

        cleanupMaidBaubleRuntimeState(maid.getUUID());
        SpellBookManager.getOrCreateManager(maid).removeMaidData(maid);
        MaidReviveEffectCleanup.cleanupBeforeNormalDeath(maid);
        Global.updateMaidInfo(maid,false);
    }

    private static boolean shouldReleaseMaidChunkLoading(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION
                || reason == Entity.RemovalReason.KILLED
                || reason == Entity.RemovalReason.DISCARDED;
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
            Global.activeMaids.add(maid);
            Global.getOrCreatePlayerMaidMap(player.getUUID()).put(maid.getUUID(), maid);
            if(maid.isOrderedToSit()&&!maid.isStructureSpawn()&&isInHiddenRetreatStructure(level, maid.blockPosition())){
                MaidOriginData.markHiddenRetreatMaid(maid);
                player.sendSystemMessage(Component.translatable("item.touhou_little_maid_spell.maid_tamed_event.maid_in_hidden_retreat").withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            // 推送末影腰包数据更新
            Global.updateMaidInfo(maid, true);
            EnderPocketBauble.pushEnderPocketDataToClient((ServerPlayer) player);
        }
    }

    @SubscribeEvent
    public static void onMaidToItem(MaidAndItemTransformEvent.ToItem event) {
        MaidOriginData.writeTransportData(event.getMaid(), event.getData());
    }

    @SubscribeEvent
    public static void onItemToMaid(MaidAndItemTransformEvent.ToMaid event) {
        MaidOriginData.readTransportData(event.getMaid(), event.getData());
    }


    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        EnderPocketService.clearRemoteSessions(event.getServer());
        clearRuntimeSpellState();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        AllianceManager.cleanupLegacyTeams(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EnderPocketService.clearRemoteSessions(event.getServer());
        clearRuntimeSpellState();
    }

    private static void clearRuntimeSpellState() {
        clearBaubleRuntimeState();
        Global.activeMaids.clear();
        Global.ownerMaidRegistry.clear();
        MaidHardRemovalProtection.clear();
        AnchorCoreBauble.clearRuntimeCache();
        YueLinglanBlockEntity.clearAllStructureSearchCaches();
        SpellBookManager.clearAll();
        MaidSuppressionZone.clear();
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
    public static void onServerTick(ServerTickEvent.Post event) {
        EnderPocketService.tickRemoteSessions(event.getServer());
        MaidHardRemovalProtection.tick(event.getServer());
        SpellBookManager.tickPendingRemovals(event.getServer());
    }

    /**
     * 玩家登录时临时加载其锚定女仆所在区块，让女仆 tick 起来由锚定核心接手强加载。
     * 记录只由锚定核心维护（换区块时覆盖，卸下或失效时删除），这里只读不删。
     */
    private static void restorePlayerMaidChunkLoading(ServerPlayer player) {
        try {
            var savedPositions = player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks();
            if (savedPositions.isEmpty()) {
                return;
            }

            int restoredCount = 0;
            for (var entry : savedPositions.entrySet()) {
                var info = entry.getValue();
                ServerLevel targetLevel = player.server.getLevel(info.levelKey());
                if (targetLevel == null) {
                    LOGGER.warn("无法找到维度 {} 来恢复女仆 {} 的区块加载", info.levelKey().location(), entry.getKey());
                    continue;
                }
                ChunkPos chunkPos = new ChunkPos(info.chunkX(), info.chunkZ());
                targetLevel.getChunkSource().addRegionTicket(MAID_ANCHOR_TICKET, chunkPos, 3, chunkPos);
                restoredCount++;
            }

            LOGGER.info("为玩家 {} 恢复了 {}/{} 个女仆的区块加载",
                player.getName().getString(), restoredCount, savedPositions.size());
        } catch (Exception e) {
            LOGGER.error("为玩家 {} 恢复女仆区块加载时发生严重错误", player.getName().getString(), e);
        }
    }

    private static boolean isControlledRetreatSpawnType(MobSpawnType spawnType) {
        return switch (spawnType) {
            case NATURAL,
                 CHUNK_GENERATION,
                 SPAWNER,
                 TRIAL_SPAWNER,
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
                .registryOrThrow(Registries.STRUCTURE)
                .getOptional(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "hidden_retreat"));

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

        var targetDimension = info.getPendingRestoreDimensionKey();
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
