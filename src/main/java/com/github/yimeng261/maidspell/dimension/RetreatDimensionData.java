package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
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

    // 玩家UUID到维度信息的映射
    private final Map<UUID, DimensionInfo> playerDimensions = new HashMap<>();

    public RetreatDimensionData() {
        super();
    }

    /**
     * 维度信息
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

        public DimensionInfo(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.createdTime = System.currentTimeMillis();
            this.lastAccessTime = this.createdTime;
            this.structureQuota = 0;
            this.foundStructurePos = null;
            this.structureGenerated = false;
        }

        public DimensionInfo(UUID playerUUID, long createdTime, long lastAccessTime, int structureQuota,
                             @Nullable BlockPos foundStructurePos, boolean structureGenerated) {
            this.playerUUID = playerUUID;
            this.createdTime = createdTime;
            this.lastAccessTime = lastAccessTime;
            this.structureQuota = structureQuota;
            this.foundStructurePos = foundStructurePos;
            this.structureGenerated = structureGenerated;
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
            return tag;
        }

        public static DimensionInfo load(CompoundTag tag) {
            UUID playerUUID = tag.getUUID("PlayerUUID");
            long createdTime = tag.getLong("CreatedTime");
            long lastAccessTime = tag.getLong("LastAccessTime");
            int structureQuota = tag.getInt("StructureQuota");
            BlockPos foundPos = null;
            if (tag.getBoolean("HasFoundStructure")) {
                foundPos = new BlockPos(
                        tag.getInt("FoundStructureX"),
                        tag.getInt("FoundStructureY"),
                        tag.getInt("FoundStructureZ")
                );
            }
            boolean structureGenerated = tag.getBoolean("StructureGenerated");

            return new DimensionInfo(playerUUID, createdTime, lastAccessTime, structureQuota, foundPos, structureGenerated);
        }
    }

    /**
     * 获取或创建数据实例
     */
    public static RetreatDimensionData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(RetreatDimensionData::new, RetreatDimensionData::load),
                DATA_NAME
        );
    }

    /**
     * 从NBT加载数据
     */
    public static RetreatDimensionData load(CompoundTag tag, HolderLookup.Provider provider) {
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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
            // 已存在则更新访问时间
            updateAccessTime(playerUUID);
        }
    }

    /**
     * 更新玩家维度的访问时间
     */
    public void updateAccessTime(UUID playerUUID) {
        DimensionInfo info = playerDimensions.get(playerUUID);
        if (info != null) {
            info.updateAccessTime();
            setDirty();
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

    /**
     * 清理长时间未访问的维度记录
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
}

