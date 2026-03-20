package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 归隐之地数据持久化
 * 保存每个玩家的归隐之地维度信息
 */
public class RetreatDimensionData extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_retreat_dimensions";
    private static final long ACCESS_TIME_UPDATE_INTERVAL_MS = 30_000L;
    
    // 存储玩家UUID到维度信息的映射
    private final Map<UUID, DimensionInfo> playerDimensions = new HashMap<>();
    
    public RetreatDimensionData() {
        super();
    }
    
    /**
     * 维度信息（扩展以支持共享模式和私人模式）
     */
    public static class DimensionInfo {
        public final UUID playerUUID;
        public long createdTime;
        public long lastAccessTime;

        // 共享模式专属字段
        public int structureQuota;      // 结构配额（0=无配额，1=有一个配额）
        @Nullable
        public BlockPos foundStructurePos; // 已找到的结构位置（持久化，共享模式下用于重复查看）

        // 私人模式专属字段
        public boolean structureGenerated; // 结构是否已生成（持久化，防止重启后重复生成）
        @Nullable
        public String pendingRestoreDimension; // 玩家下线于归隐之地时，记录待恢复的维度
        @Nullable
        public BlockPos pendingRestorePos; // 玩家下线于归隐之地时，记录待恢复的位置

        public DimensionInfo(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.createdTime = System.currentTimeMillis();
            this.lastAccessTime = this.createdTime;
            this.structureQuota = 0;
            this.foundStructurePos = null;
            this.structureGenerated = false;
            this.pendingRestoreDimension = null;
            this.pendingRestorePos = null;
        }

        public DimensionInfo(UUID playerUUID, long createdTime, long lastAccessTime, int structureQuota,
                             @Nullable BlockPos foundStructurePos, boolean structureGenerated,
                             @Nullable String pendingRestoreDimension, @Nullable BlockPos pendingRestorePos) {
            this.playerUUID = playerUUID;
            this.createdTime = createdTime;
            this.lastAccessTime = lastAccessTime;
            this.structureQuota = structureQuota;
            this.foundStructurePos = foundStructurePos;
            this.structureGenerated = structureGenerated;
            this.pendingRestoreDimension = pendingRestoreDimension;
            this.pendingRestorePos = pendingRestorePos;
        }

        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("PlayerUUID", playerUUID);
            tag.putLong("CreatedTime", createdTime);
            tag.putLong("LastAccessTime", lastAccessTime);
            tag.putInt("StructureQuota", structureQuota);
            if (foundStructurePos != null) {
                tag.putInt("FoundStructureX", foundStructurePos.getX());
                tag.putInt("FoundStructureY", foundStructurePos.getY());
                tag.putInt("FoundStructureZ", foundStructurePos.getZ());
                tag.putBoolean("HasFoundStructure", true);
            }
            tag.putBoolean("StructureGenerated", structureGenerated);
            if (pendingRestoreDimension != null && pendingRestorePos != null) {
                tag.putString("PendingRestoreDimension", pendingRestoreDimension);
                tag.putInt("PendingRestoreX", pendingRestorePos.getX());
                tag.putInt("PendingRestoreY", pendingRestorePos.getY());
                tag.putInt("PendingRestoreZ", pendingRestorePos.getZ());
            }
            return tag;
        }

        public static DimensionInfo load(CompoundTag tag) {
            UUID playerUUID = tag.getUUID("PlayerUUID");
            long createdTime = tag.getLong("CreatedTime");
            long lastAccessTime = tag.getLong("LastAccessTime");
            int structureQuota = tag.getInt("StructureQuota");
            BlockPos foundPos = null;
            if (tag.contains("HasFoundStructure") && tag.getBoolean("HasFoundStructure")) {
                foundPos = new BlockPos(
                    tag.getInt("FoundStructureX"),
                    tag.getInt("FoundStructureY"),
                    tag.getInt("FoundStructureZ")
                );
            }
            boolean structureGenerated = tag.getBoolean("StructureGenerated");
            String pendingRestoreDimension = tag.contains("PendingRestoreDimension", Tag.TAG_STRING)
                ? tag.getString("PendingRestoreDimension")
                : null;
            BlockPos pendingRestorePos = null;
            if (pendingRestoreDimension != null
                && tag.contains("PendingRestoreX", Tag.TAG_INT)
                && tag.contains("PendingRestoreY", Tag.TAG_INT)
                && tag.contains("PendingRestoreZ", Tag.TAG_INT)) {
                pendingRestorePos = new BlockPos(
                    tag.getInt("PendingRestoreX"),
                    tag.getInt("PendingRestoreY"),
                    tag.getInt("PendingRestoreZ")
                );
            }

            return new DimensionInfo(
                playerUUID,
                createdTime,
                lastAccessTime,
                structureQuota,
                foundPos,
                structureGenerated,
                pendingRestoreDimension,
                pendingRestorePos
            );
        }

        @Nullable
        public ResourceKey<Level> getPendingRestoreDimensionKey() {
            if (pendingRestoreDimension == null || pendingRestoreDimension.isBlank()) {
                return null;
            }

            ResourceLocation location = ResourceLocation.tryParse(pendingRestoreDimension);
            if (location == null) {
                return null;
            }

            @SuppressWarnings("removal")
            ResourceKey<Level> dimensionKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location);
            return dimensionKey;
        }
    }
    
    /**
     * 获取或创建数据实例
     */
    public static RetreatDimensionData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
            RetreatDimensionData::load,
            RetreatDimensionData::new,
            DATA_NAME
        );
    }
    
    /**
     * 从NBT加载数据
     */
    public static RetreatDimensionData load(CompoundTag tag) {
        RetreatDimensionData data = new RetreatDimensionData();
        
        if (tag.contains("PlayerDimensions", Tag.TAG_LIST)) {
            ListTag list = tag.getList("PlayerDimensions", Tag.TAG_COMPOUND);
            for (Tag element : list) {
                CompoundTag dimensionTag = (CompoundTag) element;
                DimensionInfo info = DimensionInfo.load(dimensionTag);
                data.playerDimensions.put(info.playerUUID, info);
            }
        }
        
        MaidSpellMod.LOGGER.info("Loaded {} retreat dimension records", data.playerDimensions.size());
        return data;
    }
    
    /**
     * 保存数据到NBT
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        
        for (DimensionInfo info : playerDimensions.values()) {
            list.add(info.save());
        }
        
        tag.put("PlayerDimensions", list);
        
        MaidSpellMod.LOGGER.debug("Saved {} retreat dimension records", playerDimensions.size());
        return tag;
    }
    
    /**
     * 注册玩家的归隐之地维度
     */
    public void registerDimension(UUID playerUUID) {
        if (!playerDimensions.containsKey(playerUUID)) {
            playerDimensions.put(playerUUID, new DimensionInfo(playerUUID));
            setDirty();
            MaidSpellMod.LOGGER.info("Registered new retreat dimension for player: " + playerUUID);
        } else {
            // 如果已存在，更新访问时间
            updateAccessTime(playerUUID);
        }
    }
    
    /**
     * 更新玩家维度的访问时间
     */
    public void updateAccessTime(UUID playerUUID) {
        DimensionInfo info = playerDimensions.get(playerUUID);
        if (info != null) {
            long now = System.currentTimeMillis();
            if ((now - info.lastAccessTime) >= ACCESS_TIME_UPDATE_INTERVAL_MS) {
                info.lastAccessTime = now;
                setDirty();
            }
        }
    }
    
    /**
     * 检查玩家是否已有归隐之地
     */
    public boolean hasDimension(UUID playerUUID) {
        return playerDimensions.containsKey(playerUUID);
    }
    
    /**
     * 获取玩家的维度信息
     */
    public DimensionInfo getDimensionInfo(UUID playerUUID) {
        return playerDimensions.get(playerUUID);
    }
    
    /**
     * 移除玩家的维度记录（用于清理）
     */
    public void removeDimension(UUID playerUUID) {
        if (playerDimensions.remove(playerUUID) != null) {
            setDirty();
            MaidSpellMod.LOGGER.info("Removed retreat dimension record for player: " + playerUUID);
        }
    }
    
    /**
     * 获取所有玩家维度信息
     */
    public Map<UUID, DimensionInfo> getAllDimensions() {
        return new HashMap<>(playerDimensions);
    }
    
    /**
     * 清理长时间未访问的维度记录（可选功能）
     */
    public void cleanupOldDimensions(long maxInactiveTime) {
        long currentTime = System.currentTimeMillis();
        playerDimensions.entrySet().removeIf(entry -> {
            DimensionInfo info = entry.getValue();
            boolean shouldRemove = (currentTime - info.lastAccessTime) > maxInactiveTime;
            if (shouldRemove) {
                MaidSpellMod.LOGGER.info("Cleaned up inactive retreat dimension for player: " + entry.getKey());
            }
            return shouldRemove;
        });

        if (!playerDimensions.isEmpty()) {
            setDirty();
        }
    }

    /**
     * 标记玩家维度的结构已生成（私人模式持久化）
     */
    public void markStructureGenerated(UUID playerUUID) {
        DimensionInfo info = playerDimensions.get(playerUUID);
        if (info != null && !info.structureGenerated) {
            info.structureGenerated = true;
            setDirty();
            MaidSpellMod.LOGGER.info("Persisted structure generated flag for player: {}", playerUUID);
        }
    }

    /**
     * 设置玩家已找到的结构位置
     */
    public void setFoundStructurePos(UUID playerUUID, @Nullable BlockPos pos) {
        DimensionInfo info = playerDimensions.get(playerUUID);
        if (info != null) {
            info.foundStructurePos = pos;
            setDirty();
            MaidSpellMod.LOGGER.info("Saved found structure position for player {}: {}", playerUUID, pos);
        }
    }

    /**
     * 获取玩家已找到的结构位置
     */
    @Nullable
    public BlockPos getFoundStructurePos(UUID playerUUID) {
        DimensionInfo info = playerDimensions.get(playerUUID);
        return info != null ? info.foundStructurePos : null;
    }

    public void setPendingRestore(UUID playerUUID, ResourceKey<Level> dimensionKey, BlockPos pos) {
        DimensionInfo info = playerDimensions.computeIfAbsent(playerUUID, DimensionInfo::new);
        info.pendingRestoreDimension = dimensionKey.location().toString();
        info.pendingRestorePos = pos.immutable();
        setDirty();
        MaidSpellMod.LOGGER.info("Saved pending retreat restore for player {}: {} @ {}", playerUUID, info.pendingRestoreDimension, pos);
    }

    public void clearPendingRestore(UUID playerUUID) {
        DimensionInfo info = playerDimensions.get(playerUUID);
        if (info != null && (info.pendingRestoreDimension != null || info.pendingRestorePos != null)) {
            info.pendingRestoreDimension = null;
            info.pendingRestorePos = null;
            setDirty();
            MaidSpellMod.LOGGER.debug("Cleared pending retreat restore for player {}", playerUUID);
        }
    }
}

