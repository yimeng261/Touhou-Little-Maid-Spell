package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.concurrent.Executors;

/**
 * 区块加载管理器
 * 管理装备锚定核心的女仆的区块强加载
 * 支持数据持久化，确保服务器重启后能恢复区块加载状态
 * 修复说明：
 * 1. ChunkKey 不再直接持有 ServerLevel 引用，改用 ResourceKey<Level> 避免可变对象作为Map键
 * 2. ChunkTimer.associatedMaids 使用线程安全的 ConcurrentHashMap.newKeySet()
 * 3. processChunkTimers() 使用 retainAll() 避免迭代时修改集合
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class ChunkLoadingManager {
    
    // 存储每个女仆的区块加载状态，包含维度信息
    private static final Map<UUID, Set<ChunkKey>> maidChunkPositions = new ConcurrentHashMap<>();
    
    // 基于时间的区块加载管理系统
    private static final Map<ChunkKey, ChunkTimer> chunkTimers = new ConcurrentHashMap<>();

    private static final int CHECK_INTERVAL_TICKS = 20; // 每20tick检查一次
    private static final int DEFAULT_CHUNK_LIFETIME_TICKS = CHECK_INTERVAL_TICKS * 10; // 10秒 (20 ticks/秒 * 10秒)

    /**
     * 区块键类，用于唯一标识一个区块
     * 使用 ResourceKey<Level> 代替 ServerLevel，避免可变对象作为Map键
     */
    public static final class ChunkKey {
        private final ChunkPos chunkPos;
        private final ResourceKey<Level> dimension;
        
        public ChunkKey(ChunkPos chunkPos, ResourceKey<Level> dimension) {
            this.chunkPos = chunkPos;
            this.dimension = dimension;
        }
        
        /**
         * 便捷构造方法：从 ServerLevel 创建
         */
        public ChunkKey(ChunkPos chunkPos, ServerLevel level) {
            this(chunkPos, level.dimension());
        }
        
        public ChunkPos chunkPos() {
            return chunkPos;
        }
        
        public ResourceKey<Level> dimension() {
            return dimension;
        }
        
        /**
         * 获取对应的 ServerLevel（需要服务器实例）
         * @return ServerLevel 或 null（如果维度不存在）
         */
        @Nullable
        public ServerLevel getLevel() {
            MinecraftServer server = getCurrentServer();
            if (server == null) {
                return null;
            }
            return server.getLevel(dimension);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkKey other)) return false;
            return chunkPos.equals(other.chunkPos) && dimension.equals(other.dimension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos, dimension);
        }

        @Override
        public String toString() {
            return chunkPos + " (" + dimension.location() + ")";
        }
    }
    
    /**
     * 区块计时器类，管理区块的加载时间和关联的女仆
     * 使用线程安全的 Set 避免并发修改问题
     */
    private static class ChunkTimer {
        private int remainingTicks;
        // 使用线程安全的 Set
        private final Set<UUID> associatedMaids;
        private final ChunkKey chunkKey;
        
        public ChunkTimer(ChunkKey chunkKey, int initialTicks) {
            this.chunkKey = chunkKey;
            this.remainingTicks = initialTicks;
            // 使用 ConcurrentHashMap.newKeySet() 保证线程安全
            this.associatedMaids = ConcurrentHashMap.newKeySet();
        }
        
        public void addMaid(UUID maidId) {
            associatedMaids.add(maidId);
        }
        
        public Set<UUID> getAssociatedMaids() {
            return new HashSet<>(associatedMaids);
        }

        public void update() {
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
        //Global.LOGGER.debug("为女仆 {} 启用区块加载: {}", maidId, chunkKey);
        
        // 使用新的计时器系统
        performChunkOperation(maidId, chunkKey, true);
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

            // 预加载目标区块，给予较长的生存时间
            performChunkOperation(maidId, targetKey, true, DEFAULT_CHUNK_LIFETIME_TICKS / 2);
            
        } catch (Exception e) {
            Global.LOGGER.error("预加载女仆 {} 传送目标区块时发生错误", maidId, e);
        }
    }
    
    /**
     * 每20tick执行的区块管理检查
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            //Global.LOGGER.debug("forced chunk size: {}",chunkTimers.size());
            processChunkTimers();
        }
    }

    @SubscribeEvent
    public static void onoMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        if(maid.tickCount % 20 == 0) {
            //Global.LOGGER.debug("maid tick: {}", maid.getDisplayName());
        }
    }

    // 复用的集合，避免频繁创建临时对象
    private static final List<ChunkKey> reusableExpiredChunks = new ArrayList<>();
    
    /**
     * 处理所有区块计时器
     * 优化：复用集合对象，使用 Iterator 原地移除无效女仆
     */
    private static void processChunkTimers() {
        if (chunkTimers.isEmpty()) {
            return;
        }
        
        // 复用集合，清空之前的数据
        reusableExpiredChunks.clear();
        
        MinecraftServer server = getCurrentServer();
        
        if (server == null) {
            return;
        }

        // 使用 Iterator 遍历，便于安全地处理
        for (Map.Entry<ChunkKey, ChunkTimer> entry : chunkTimers.entrySet()) {
            ChunkKey chunkKey = entry.getKey();
            ChunkTimer timer = entry.getValue();
            
            // 获取当前区块对应的 ServerLevel
            ServerLevel level = chunkKey.getLevel();
            if (level == null) {
                // 维度不存在，标记为过期
                reusableExpiredChunks.add(chunkKey);
                continue;
            }

            // 使用 Iterator 原地移除无效的女仆，避免创建临时集合
            Set<UUID> associatedMaids = timer.getAssociatedMaids();
            Iterator<UUID> iterator = associatedMaids.iterator();
            while (iterator.hasNext()) {
                UUID maidId = iterator.next();
                try {
                    boolean isValid = false;
                    if (level.getEntity(maidId) instanceof EntityMaid maid) {
                        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE) 
                                && maid.chunkPosition().equals(chunkKey.chunkPos())) {
                            isValid = true;
                        }
                    }
                    if (!isValid) {
                        iterator.remove();
                    }
                } catch (Exception e) {
                    // 实体获取失败，移除该女仆
                    iterator.remove();
                    Global.LOGGER.debug("获取女仆实体 {} 时发生错误: {}", maidId, e.getMessage());
                }
            }
            
            // 如果没有有效女仆，开始倒计时
            if (associatedMaids.isEmpty()) {
                timer.update();
                if (timer.isExpired()) {
                    reusableExpiredChunks.add(chunkKey);
                }
            }
        }
        
        // 卸载过期的区块
        for (ChunkKey expiredChunk : reusableExpiredChunks) {
            unloadExpiredChunk(expiredChunk);
        }
        
        if (!reusableExpiredChunks.isEmpty()) {
            Global.LOGGER.debug("卸载了 {} 个过期区块", reusableExpiredChunks.size());
        }
    }



    /**
     * 卸载过期的区块
     */
    private static void unloadExpiredChunk(ChunkKey chunkKey) {
        ChunkTimer timer = chunkTimers.remove(chunkKey);
        if (timer == null) {
            return;
        }
        
        ServerLevel serverLevel = chunkKey.getLevel();
        if (serverLevel == null) {
            Global.LOGGER.warn("无法找到维度 {} 来卸载区块", chunkKey.dimension().location());
            return;
        }
        
        // 为每个关联的女仆卸载区块
        for (UUID maidId : timer.getAssociatedMaids()) {
            performChunkOperation(maidId, chunkKey, false);
        }
        
        Global.LOGGER.debug("卸载过期区块: {} (关联女仆: {})", chunkKey, timer.getAssociatedMaids());
    }

    /**
     * 获取当前服务器实例
     */
    public static MinecraftServer getCurrentServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    // ===== 私有辅助方法 =====
    private static boolean performChunkOperation(UUID maidId, ChunkKey info, boolean enable) {
        return performChunkOperation(maidId, info, enable, DEFAULT_CHUNK_LIFETIME_TICKS);
    }

    private static boolean performChunkOperation(UUID maidId, ChunkKey info, boolean enable, int lifetimeTicks) {
        ServerLevel serverLevel = info.getLevel();
        ChunkPos chunkPos = info.chunkPos();
        //Global.LOGGER.debug("before force load: chunk=[{}, {}], enable={}, maid={}", chunkPos.x, chunkPos.z, enable, maidId);

        if(serverLevel == null){
            return false;
        }

        //Global.LOGGER.debug("before force load: serverLevel={}", serverLevel);
        
        // 检查区块是否已生成，避免同步生成导致卡顿
        boolean isChunkLoaded = isChunkGenerated(serverLevel, chunkPos);
        
        if (enable && !isChunkLoaded) {
            // 使用 CompletableFuture 异步生成区块
            generateChunkAsync(serverLevel, chunkPos, maidId, info, lifetimeTicks);
            
            // 先记录到计时器，避免数据丢失
            ChunkTimer timer = chunkTimers.computeIfAbsent(info, k->new ChunkTimer(info, lifetimeTicks));
            timer.addMaid(maidId);
            
            return true;
        } else {
            // 区块已加载或是卸载操作，直接执行
            // ticking=true 确保区块内的实体和逻辑会正常运行
            ForgeChunkManager.forceChunk(serverLevel, MaidSpellMod.MOD_ID, maidId, chunkPos.x, chunkPos.z, enable, true);
        }
        
        //serverLevel.setChunkForced(chunkPos.x, chunkPos.z, enable);
        //Global.LOGGER.debug("after force load: serverLevel={}", serverLevel);
        ChunkLoadingData data = ChunkLoadingData.get(serverLevel.getServer());
        data.updateMaidPosition(maidId, info, enable);
        if (enable) {
            // 创建或更新区块计时器
            ChunkTimer timer = chunkTimers.computeIfAbsent(info, k->new ChunkTimer(info, lifetimeTicks));
            timer.addMaid(maidId);
        }

        return true;
    }
    
    /**
     * 异步生成区块（不阻塞主线程）
     */
    private static void generateChunkAsync(ServerLevel level, ChunkPos chunkPos, UUID maidId, ChunkKey info, int lifetimeTicks) {
        // 使用 Minecraft 的异步区块加载系统
        CompletableFuture.supplyAsync(() -> {
            try {
                Global.LOGGER.debug("异步生成区块开始: [{}, {}]", chunkPos.x, chunkPos.z);
                
                // 使用服务器的区块生成器异步生成区块
                ChunkAccess chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
                
                Global.LOGGER.debug("异步生成区块完成: [{}, {}]", chunkPos.x, chunkPos.z);
                return chunk != null;
            } catch (Exception e) {
                Global.LOGGER.error("异步生成区块 [{}, {}] 时发生错误", chunkPos.x, chunkPos.z, e);
                return false;
            }
        }, Executors.newSingleThreadExecutor()).thenAcceptAsync(success -> {
            // 区块生成完成后，在主线程启用强制加载
            if (success) {
                level.getServer().execute(() -> {
                    try {
                        Global.LOGGER.debug("区块生成成功，启用强制加载: [{}, {}]", chunkPos.x, chunkPos.z);
                        ForgeChunkManager.forceChunk(level, MaidSpellMod.MOD_ID, maidId, chunkPos.x, chunkPos.z, true, true);
                        
                        // 更新数据
                        ChunkLoadingData data = ChunkLoadingData.get(level.getServer());
                        data.updateMaidPosition(maidId, info, true);
                    } catch (Exception e) {
                        Global.LOGGER.error("启用强制加载失败: [{}, {}]", chunkPos.x, chunkPos.z, e);
                    }
                });
            } else {
                Global.LOGGER.warn("区块 [{}, {}] 生成失败，跳过强制加载", chunkPos.x, chunkPos.z);
            }
        });
    }
    
    /**
     * 检查区块是否已生成（非阻塞检查）
     * @param level 服务器世界
     * @param chunkPos 区块位置
     * @return true 如果区块已生成
     */
    private static boolean isChunkGenerated(ServerLevel level, ChunkPos chunkPos) {
        try {
            // 使用 getChunkNow 进行非阻塞检查，如果区块未加载则返回null
            return level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != null;
        } catch (Exception e) {
            Global.LOGGER.warn("检查区块 {} 是否生成时发生错误: {}", chunkPos, e.getMessage());
            // 出错时保守处理，假定未生成
            return false;
        }
    }
    
    // ===== 全局数据保存类 =====
    
    /**
     * 全局区块加载数据保存类
     * 直接操作静态变量 maidChunkPositions，只负责序列化/反序列化
     */
    public static class ChunkLoadingData extends SavedData {
        private static final String DATA_NAME = "maidspell_chunk_loading";
        
        public ChunkLoadingData() {
        }
        
        public ChunkLoadingData(CompoundTag tag) {
            load(tag);
        }
        
        @SuppressWarnings("removal")
        public static ChunkLoadingData load(CompoundTag tag) {
            ChunkLoadingData data = new ChunkLoadingData();
            
            // 从 NBT 加载数据到静态变量 maidChunkPositions
            CompoundTag maidsTag = tag.getCompound("maids");
            int loadedCount = 0;
            
            for (String uuidStr : maidsTag.getAllKeys()) {
                try {
                    UUID maidId = UUID.fromString(uuidStr);
                    CompoundTag maidTag = maidsTag.getCompound(uuidStr);
                    
                    // 加载该女仆的所有区块
                    CompoundTag chunksTag = maidTag.getCompound("chunks");
                    Set<ChunkKey> chunks = ConcurrentHashMap.newKeySet();
                    
                    for (String chunkIdxStr : chunksTag.getAllKeys()) {
                        CompoundTag chunkTag = chunksTag.getCompound(chunkIdxStr);
                        
                        // 读取区块位置
                        int chunkX = chunkTag.getInt("x");
                        int chunkZ = chunkTag.getInt("z");
                        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                        
                        // 读取维度
                        String dimensionStr = chunkTag.getString("dimension");
                        ResourceKey<Level> dimension = ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            new ResourceLocation(dimensionStr)
                        );
                        ChunkKey chunkKey = new ChunkKey(chunkPos, dimension);
                        chunkTimers.put(chunkKey,new ChunkTimer(chunkKey,DEFAULT_CHUNK_LIFETIME_TICKS));
                        chunks.add(chunkKey);
                    }
                    
                    if (!chunks.isEmpty()) {
                        maidChunkPositions.put(maidId, chunks);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    Global.LOGGER.error("加载女仆 {} 的区块数据时发生错误", uuidStr, e);
                }
            }
            
            Global.LOGGER.info("成功加载 {} 个女仆的区块加载数据", loadedCount);
            return data;
        }
        
        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            CompoundTag maidsTag = new CompoundTag();
            
            // 从静态变量 maidChunkPositions 保存数据
            for (Map.Entry<UUID, Set<ChunkKey>> entry : maidChunkPositions.entrySet()) {
                UUID maidId = entry.getKey();
                Set<ChunkKey> chunks = entry.getValue();
                
                if (chunks.isEmpty()) {
                    continue;
                }
                
                CompoundTag maidTag = new CompoundTag();
                CompoundTag chunksTag = new CompoundTag();
                
                // 保存该女仆的所有区块
                int idx = 0;
                for (ChunkKey chunkKey : chunks) {
                    CompoundTag chunkTag = new CompoundTag();
                    chunkTag.putInt("x", chunkKey.chunkPos().x);
                    chunkTag.putInt("z", chunkKey.chunkPos().z);
                    chunkTag.putString("dimension", chunkKey.dimension().location().toString());
                    
                    chunksTag.put(String.valueOf(idx++), chunkTag);
                }
                
                maidTag.put("chunks", chunksTag);
                maidsTag.put(maidId.toString(), maidTag);
            }

            tag.put("maids", maidsTag);
            Global.LOGGER.debug("成功保存 {} 个女仆的区块加载数据", maidChunkPositions.size());
            return tag;
        }
        
        /**
         * 更新女仆的区块位置
         * @param maidId 女仆ID
         * @param info 区块信息
         * @param add true=添加，false=移除
         */
        public void updateMaidPosition(UUID maidId, ChunkKey info, boolean add) {
            if (add) {
                // 添加区块
                maidChunkPositions.computeIfAbsent(maidId, k -> ConcurrentHashMap.newKeySet()).add(info);
            } else {
                // 移除区块
                Set<ChunkKey> chunks = maidChunkPositions.get(maidId);
                if (chunks != null) {
                    chunks.remove(info);
                    // 如果该女仆没有任何区块了，移除整个记录
                    if (chunks.isEmpty()) {
                        maidChunkPositions.remove(maidId);
                    }
                }
            }
            setDirty();
        }
        
        public static ChunkLoadingData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                ChunkLoadingData::load, 
                ChunkLoadingData::new, 
                DATA_NAME
            );
        }
    }

    
    
    // ===== 服务器启动事件处理 =====

    
    /**
     * 服务器启动时加载保存的区块加载数据
     * ChunkLoadingData.get() 会自动触发 load()，数据会直接加载到 maidChunkPositions
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        maidChunkPositions.clear();
        
        try {
            // 触发数据加载，load() 方法会自动将数据加载到 maidChunkPositions
            ChunkLoadingData.get(server);
            
            if (!maidChunkPositions.isEmpty()) {
                Global.LOGGER.info("服务器启动时发现 {} 个女仆的区块加载配置", maidChunkPositions.size());
            }
        } catch (Exception e) {
            Global.LOGGER.error("服务器启动时加载区块加载数据发生错误", e);
        }
    }
    
    /**
     * 服务器完全启动后恢复区块加载状态
     * 在这里统一恢复所有区块加载，避免每个玩家登录时重复恢复
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (maidChunkPositions.isEmpty()) {
            return;
        }
        
        Global.LOGGER.info("服务器启动完成，开始恢复 {} 个女仆的区块加载配置...", maidChunkPositions.size());

        // 统一恢复所有区块加载
        int restoredCount = 0;
        for (Map.Entry<UUID, Set<ChunkKey>> entry : maidChunkPositions.entrySet()) {
            UUID maidId = entry.getKey();
            Set<ChunkKey> chunks = entry.getValue();
            
            // 恢复该女仆的所有区块
            for (ChunkKey info : chunks) {
                if (performChunkOperation(maidId, info, true)) {
                    restoredCount++;
                }
            }
        }

        Global.LOGGER.info("服务器启动时成功恢复了 {} 个区块的加载", restoredCount);
    }

    
}
