package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块加载管理器
 * 管理装备锚定核心的女仆的区块强加载
 * 支持数据持久化，确保服务器重启后能恢复区块加载状态
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class ChunkLoadingManager {
    
    // 存储每个女仆的区块加载状态，包含维度信息
    public static final Map<UUID, ChunkKey> maidChunkPositions = new ConcurrentHashMap<>();
    
    // 基于时间的区块加载管理系统
    private static final Map<ChunkKey, ChunkTimer> chunkTimers = new ConcurrentHashMap<>();

    private static final int CHECK_INTERVAL_TICKS = 20; // 每20tick检查一次
    private static final int DEFAULT_CHUNK_LIFETIME_TICKS = CHECK_INTERVAL_TICKS*10; // 10秒 (20 ticks/秒 * 10秒)

    /**
     * 区块键类，用于唯一标识一个区块
     */
    public record ChunkKey(ChunkPos chunkPos, ServerLevel level) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkKey other)) return false;
            return chunkPos.equals(other.chunkPos) && level.equals(other.level);
        }

        @Override
        public String toString() {
            return chunkPos + " (" + level + ")";
        }
    }
    
    /**
     * 区块计时器类，管理区块的加载时间和关联的女仆
     */
    private static class ChunkTimer {
        private int remainingTicks;
        private final Set<UUID> associatedMaids;
        private final ChunkKey chunkKey;
        
        public ChunkTimer(ChunkKey chunkKey, int initialTicks) {
            this.chunkKey = chunkKey;
            this.remainingTicks = initialTicks;
            this.associatedMaids = new HashSet<>();
        }
        
        public void addMaid(UUID maidId) {
            associatedMaids.add(maidId);
        }
        
        public Set<UUID> getAssociatedMaids() {
            return associatedMaids;
        }

        public void update(){
            remainingTicks = Math.max(0, remainingTicks - CHECK_INTERVAL_TICKS);
        }
        
        public boolean isExpired() {
            return remainingTicks <= 0;
        }
    
        
        @Override
        public String toString() {
            return String.format("ChunkTimer{%s, ticks=%d, maids=%s}", 
                chunkKey, remainingTicks, associatedMaids);
        }
    }
    
    /**
     * 为女仆启用区块加载（使用新的计时器系统）
     * @param maid 女仆实体
     */
    public static void enableChunkLoading(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        
        UUID maidId = maid.getUUID();
        ServerLevel serverLevel = (ServerLevel) maid.level();
        ChunkPos chunkPos = maid.chunkPosition();
        


        ChunkKey chunkKey = new ChunkKey(chunkPos, serverLevel);
        
        Global.LOGGER.debug("为女仆 {} 启用区块加载: {}", maidId, chunkKey);
        
        // 使用新的计时器系统
        enableChunkLoadingWithTimer(maidId, serverLevel, chunkKey, DEFAULT_CHUNK_LIFETIME_TICKS);
        maidChunkPositions.put(maidId, chunkKey);

    }

    
    /**
     * 使用计时器系统启用区块加载
     */
    private static void enableChunkLoadingWithTimer(UUID maidId, ServerLevel serverLevel, 
                                                   ChunkKey chunkKey, int lifetimeTicks) {
        ChunkTimer timer = chunkTimers.get(chunkKey);
        if (timer == null) {
            // 创建新的计时器并加载区块
            timer = new ChunkTimer(chunkKey, lifetimeTicks);
            timer.addMaid(maidId);
            Global.LOGGER.debug("准备进行区块加载");
            boolean success = performChunkOperation(serverLevel, maidId, chunkKey.chunkPos, true);
            if (success) {
                chunkTimers.put(chunkKey, timer);
                Global.LOGGER.debug("新建区块计时器: {} ({}秒)", chunkKey, lifetimeTicks / 20);
                saveToGlobalData(serverLevel.getServer());
            } else {
                Global.LOGGER.warn("无法为女仆 {} 启用区块加载: {}", maidId, chunkKey);
            }
        }
    }
    
    /**
     * 预加载传送目标区块
     * @param maid 女仆实体
     * @param targetPos 目标位置
     * @param level 目标维度
     */
    public static void preloadTeleportTarget(EntityMaid maid, Vec3 targetPos, ServerLevel level) {
        if (maid == null || targetPos == null) {
            return;
        }
        
        UUID maidId = maid.getUUID();
        ChunkPos targetChunk = new ChunkPos(BlockPos.containing(targetPos));
        ChunkKey targetKey = new ChunkKey(targetChunk, level);
        
        Global.LOGGER.debug("女仆 {} 预加载传送目标区块: {}", maidId, targetKey);
        
        try {
            MinecraftServer server = maid.getServer();
            if (server == null) return;

            if (level == null) {
                Global.LOGGER.warn("无法找到目标维度 {} 来预加载区块", level);
                return;
            }
            
            // 预加载目标区块，给予较长的生存时间
            enableChunkLoadingWithTimer(maidId, level, targetKey, DEFAULT_CHUNK_LIFETIME_TICKS/2);
            
        } catch (Exception e) {
            Global.LOGGER.error("预加载女仆 {} 传送目标区块时发生错误", maidId, e);
        }
    }
    
    /**
     * 每20tick执行的区块管理检查
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            processChunkTimers();
        }
    }
    
    /**
     * 处理所有区块计时器
     */
    private static void processChunkTimers() {
        if (chunkTimers.isEmpty()) {
            return;
        }
        
        List<ChunkKey> expiredChunks = new ArrayList<>();
        MinecraftServer server = getCurrentServer();
        
        if (server == null) {
            return;
        }

        //Global.LOGGER.debug("检查 {} 个区块计时器", chunkTimers.size());
        
        for (Map.Entry<ChunkKey, ChunkTimer> entry : chunkTimers.entrySet()) {
            ChunkKey chunkKey = entry.getKey();
            ChunkTimer timer = entry.getValue();

            //Global.LOGGER.debug("区块计时器: {} 剩余{}秒，关联女仆: {}", chunkKey, timer.getRemainingTicks() / 20, timer.getAssociatedMaids().size());

            Set<UUID> validMaids = new HashSet<>();

            for (UUID maidId : timer.getAssociatedMaids()) {
                if(timer.chunkKey.level.getEntity(maidId) instanceof EntityMaid maid) {
                    if (BaubleStateManager.hasBauble(maid,MaidSpellItems.ANCHOR_CORE) && maid.chunkPosition().equals(timer.chunkKey.chunkPos)) {
                        validMaids.add(maidId);
                    }
                }
            }

            // 更新计时器的女仆列表
            timer.getAssociatedMaids().clear();
            for (UUID validMaid : validMaids) {
                timer.addMaid(validMaid);
            }
            
            if (validMaids.isEmpty()) {
                timer.update();
                if (timer.isExpired()) {
                    expiredChunks.add(chunkKey);
                }
            }
        }
        
        // 卸载过期的区块
        for (ChunkKey expiredChunk : expiredChunks) {
            unloadExpiredChunk(expiredChunk, server);
        }
        
        if (!expiredChunks.isEmpty()) {
            Global.LOGGER.debug("卸载了 {} 个过期区块", expiredChunks.size());
        }
    }



    /**
     * 卸载过期的区块
     */
    private static void unloadExpiredChunk(ChunkKey chunkKey, MinecraftServer server) {
        ChunkTimer timer = chunkTimers.remove(chunkKey);
        if (timer == null) {
            return;
        }
        
        ServerLevel serverLevel = chunkKey.level;
        if (serverLevel == null) {
            Global.LOGGER.warn("无法找到维度 {} 来卸载区块", chunkKey.level);
            return;
        }
        
        // 为每个关联的女仆卸载区块
        for (UUID maidId : timer.getAssociatedMaids()) {
            boolean success = performChunkOperation(serverLevel, maidId, chunkKey.chunkPos, false);
            if (success) {
                // 清理女仆位置记录
                ChunkKey maidInfo = maidChunkPositions.get(maidId);
                if (maidInfo != null && 
                    maidInfo.chunkPos.equals(chunkKey.chunkPos) && 
                    maidInfo.level.equals(chunkKey.level)) {
                    maidChunkPositions.remove(maidId);
                }
            }
        }
        
        Global.LOGGER.debug("卸载过期区块: {} (关联女仆: {})", chunkKey, timer.getAssociatedMaids());
        
        // 保存全局数据
        saveToGlobalData(server);
    }
    
    /**
     * 获取当前服务器实例
     */
    private static MinecraftServer getCurrentServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    /**
     * 检查女仆是否应该启用区块加载
     */
    public static boolean shouldEnableChunkLoading(EntityMaid maid, MinecraftServer server) {
        UUID maidId = maid.getUUID();
        
        // 1. 首先检查内存中是否已有记录
        if (maidChunkPositions.containsKey(maidId)) {
            return true;
        }
        
        // 2. 检查全局SavedData中是否有记录
        try {
            ChunkLoadingData data = ChunkLoadingData.get(server);
            return data.getSavedPositions().containsKey(maidId);
        } catch (Exception e) {
            Global.LOGGER.warn("检查全局区块加载数据时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    
    /**
     * 从全局数据恢复女仆的区块加载状态
     */
    public static void restoreChunkLoadingFromSavedData(EntityMaid maid, MinecraftServer server) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        
        UUID maidId = maid.getUUID();
        
        try {
            ChunkLoadingData data = ChunkLoadingData.get(server);
            ChunkKey savedInfo = data.getMaidRecord(maidId);
            
            if (savedInfo == null) {
                // 没有保存的记录，启用新的区块加载
                enableChunkLoading(maid);
                return;
            }
            
            ServerLevel serverLevel = (ServerLevel) maid.level();
            ChunkPos currentChunk = maid.chunkPosition();
            ResourceKey<Level> currentDimension = serverLevel.dimension();
            
            if (currentDimension.equals(savedInfo.level.dimension()) &&
                currentChunk.equals(savedInfo.chunkPos)) {
                // 女仆还在原来的位置，直接恢复
                restoreChunkLoadingAtPosition(maid, serverLevel, savedInfo);
            } else {
                // 女仆位置发生了变化，更新到新位置
                Global.LOGGER.debug("女仆 {} 位置已变化，更新区块加载到新位置: 从 {} 到 {}", 
                    maid.getUUID(), savedInfo, new ChunkKey(currentChunk, serverLevel));
                enableChunkLoading(maid);
            }
        } catch (Exception e) {
            Global.LOGGER.error("从SavedData恢复女仆 {} 区块加载时发生错误", maid.getUUID(), e);
        }
    }
    
    // ===== 私有辅助方法 =====

    private static boolean performChunkOperation(ServerLevel serverLevel, UUID maidId,
                                                        ChunkPos chunkPos, boolean enable) {

        try {
            Global.LOGGER.debug("before force load: chunk=[{}, {}], enable={}, maid={}", 
                chunkPos.x, chunkPos.z, enable, maidId);

            if (enable && serverLevel.getForcedChunks().contains(chunkPos.toLong())) {
                Global.LOGGER.debug("区块 {} 已经被强制加载，跳过", chunkPos);
                return true;
            }

            return ForgeChunkManager.forceChunk(
                    serverLevel,
                    MaidSpellMod.MOD_ID,
                    maidId,
                    chunkPos.x,
                    chunkPos.z,
                    enable,
                    true
            );
        } catch (Exception e) {
            Global.LOGGER.error("区块操作失败: chunk=[{}, {}], enable={}, error={}", 
                chunkPos.x, chunkPos.z, enable, e.getMessage(), e);
            return false;
        }
    }


    /**
     * 在指定位置恢复区块加载
     */
    private static void restoreChunkLoadingAtPosition(EntityMaid maid, ServerLevel serverLevel, ChunkKey savedInfo) {
        UUID maidId = maid.getUUID();
        boolean success = performChunkOperation(serverLevel, maidId, savedInfo.chunkPos, true);
        
        if (success) {
            maidChunkPositions.put(maidId, savedInfo);
        }
    }
    
    // ===== 全局数据保存类 =====
    
    /**
     * 全局区块加载数据保存类
     */
    public static class ChunkLoadingData extends SavedData {
        private static final String DATA_NAME = "maidspell_chunk_loading";
        private final Map<UUID, ChunkKey> savedPositions = new HashMap<>();
        
        public ChunkLoadingData() {
        }
        
        public ChunkLoadingData(CompoundTag tag) {
            load(tag);
        }
        
        @SuppressWarnings("removal")
        public static ChunkLoadingData load(CompoundTag tag) {
            ChunkLoadingData data = new ChunkLoadingData();
            
            CompoundTag maidsTag = tag.getCompound("maids");
            for (String uuidStr : maidsTag.getAllKeys()) {
                try {
                    UUID maidId = UUID.fromString(uuidStr);
                    CompoundTag maidTag = maidsTag.getCompound(uuidStr);
                    
                    int x = maidTag.getInt("x");
                    int z = maidTag.getInt("z");
                    String dimensionStr = maidTag.getString("level");

                    // 解析维度资源键
                    ResourceKey<Level> dimension = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(dimensionStr)
                    );
                    
                    // 获取当前服务器实例
                    MinecraftServer server = getCurrentServer();
                    if (server == null) {
                        Global.LOGGER.warn("无法获取服务器实例，跳过女仆 {} 的区块加载数据", maidId);
                        continue;
                    }
                    
                    // 获取对应维度的ServerLevel
                    ServerLevel level = server.getLevel(dimension);
                    if (level == null) {
                        Global.LOGGER.warn("无法找到维度 {}，跳过女仆 {} 的区块加载数据", dimensionStr, maidId);
                        continue;
                    }
                    
                    data.savedPositions.put(maidId, new ChunkKey(new ChunkPos(x, z), level));
                    Global.LOGGER.debug("成功加载女仆 {} 的区块加载数据: 位置({}, {}) 维度{}", 
                        maidId, x, z, dimensionStr);
                } catch (Exception e) {
                    Global.LOGGER.warn("加载女仆 {} 区块加载数据时发生错误: {}", uuidStr, e.getMessage());
                }
            }
            
            Global.LOGGER.info("成功加载 {} 个女仆的区块加载数据", data.savedPositions.size());
            return data;
        }
        
        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            CompoundTag maidsTag = new CompoundTag();
            
            for (Map.Entry<UUID, ChunkKey> entry : savedPositions.entrySet()) {
                try {
                    CompoundTag maidTag = new CompoundTag();
                    ChunkKey info = entry.getValue();
                    
                    maidTag.putInt("x", info.chunkPos.x);
                    maidTag.putInt("z", info.chunkPos.z);
                    // 正确保存维度资源键
                    maidTag.putString("level", info.level.dimension().location().toString());
                    
                    maidsTag.put(entry.getKey().toString(), maidTag);
                    
                    Global.LOGGER.debug("保存女仆 {} 的区块加载数据: 位置({}, {}) 维度{}", 
                        entry.getKey(), info.chunkPos.x, info.chunkPos.z, 
                        info.level.dimension().location().toString());
                } catch (Exception e) {
                    Global.LOGGER.warn("保存女仆 {} 区块加载数据时发生错误: {}", 
                        entry.getKey(), e.getMessage());
                }
            }
            
            tag.put("maids", maidsTag);
            Global.LOGGER.debug("成功保存 {} 个女仆的区块加载数据", savedPositions.size());
            return tag;
        }
        
        public void updateMaidPosition(UUID maidId, ChunkKey info) {
            if (info != null) {
                savedPositions.put(maidId, info);
            } else {
                savedPositions.remove(maidId);
            }
            setDirty();
        }
        
        public Map<UUID, ChunkKey> getSavedPositions() {
            return new HashMap<>(savedPositions);
        }
        
        public boolean hasMaidRecord(UUID maidId) {
            return savedPositions.containsKey(maidId);
        }
        
        public ChunkKey getMaidRecord(UUID maidId) {
            return savedPositions.get(maidId);
        }
        
        public static ChunkLoadingData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                ChunkLoadingData::load, 
                ChunkLoadingData::new, 
                DATA_NAME
            );
        }
    }
    
    /**
     * 保存到全局数据
     */
    private static void saveToGlobalData(MinecraftServer server) {
        try {
            ChunkLoadingData data = ChunkLoadingData.get(server);
            // 同步当前内存中的数据到全局保存数据
            for (Map.Entry<UUID, ChunkKey> entry : maidChunkPositions.entrySet()) {
                data.updateMaidPosition(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            Global.LOGGER.error("保存区块加载全局数据时发生错误", e);
        }
    }
    
    // ===== 服务器启动事件处理 =====
    
    /**
     * 服务器启动时恢复区块加载状态
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        
        try {
            // 从全局数据中加载保存的区块加载信息
            ChunkLoadingData data = ChunkLoadingData.get(server);
            Map<UUID, ChunkKey> savedPositions = data.getSavedPositions();
            
            if (!savedPositions.isEmpty()) {
                Global.LOGGER.info("服务器启动时发现 {} 个女仆的区块加载配置，将在女仆加载时自动恢复", savedPositions.size());
                
                // 将保存的数据加载到内存中，等待女仆实体加载时恢复
                maidChunkPositions.putAll(savedPositions);
            }
        } catch (Exception e) {
            Global.LOGGER.error("服务器启动时加载区块加载数据发生错误", e);
        }
    }
    
}
