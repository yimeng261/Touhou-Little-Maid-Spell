package com.github.yimeng261.maidspell.player;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 主人不在线时要改的锚定记录：女仆换了区块记新位置，死亡、被移除或卸下锚定核心记删除。
 * <p>
 * 锚定记录存在主人身上，主人离线时改不了，先记在这里；读入离线玩家的记录时和主人登录时各应用一次，
 * 登录写进主人身上后清掉。同一只女仆只留最后一次变更。存在主世界的 data/ 目录。
 */
public class AnchorPendingChangeData extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_anchor_pending_removals";

    /** 主人 → 女仆 → 新位置；空表示删除 */
    private final Map<UUID, Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>>> changesByOwner = new HashMap<>();

    private AnchorPendingChangeData() {
    }

    private static AnchorPendingChangeData load(CompoundTag tag, HolderLookup.Provider registries) {
        AnchorPendingChangeData data = new AnchorPendingChangeData();
        // 只记删除的格式：Owners → 主人 → 女仆 UUID 列表
        CompoundTag removals = tag.getCompound("Owners");
        for (String ownerKey : removals.getAllKeys()) {
            UUID owner = parseUuid(ownerKey);
            if (owner != null) {
                for (Tag maidTag : removals.getList(ownerKey, Tag.TAG_INT_ARRAY)) {
                    data.changesOf(owner).put(NbtUtils.loadUUID(maidTag), Optional.empty());
                }
            }
        }
        // Changes → 主人 → 女仆 → 新位置，删除记为空的 CompoundTag
        CompoundTag changes = tag.getCompound("Changes");
        for (String ownerKey : changes.getAllKeys()) {
            UUID owner = parseUuid(ownerKey);
            if (owner == null) {
                continue;
            }
            CompoundTag maids = changes.getCompound(ownerKey);
            for (String maidKey : maids.getAllKeys()) {
                UUID maid = parseUuid(maidKey);
                CompoundTag change = maids.getCompound(maidKey);
                if (maid == null) {
                    continue;
                }
                if (change.isEmpty()) {
                    data.changesOf(owner).put(maid, Optional.empty());
                } else {
                    ChunkLoadingData.LevelAndChunkPos.CODEC.parse(NbtOps.INSTANCE, change).result()
                            .ifPresent(pos -> data.changesOf(owner).put(maid, Optional.of(pos)));
                }
            }
        }
        data.changesByOwner.values().removeIf(Map::isEmpty);
        return data;
    }

    public static AnchorPendingChangeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AnchorPendingChangeData::new, AnchorPendingChangeData::load),
                DATA_NAME);
    }

    /** 记下一条变更：{@code pos} 为空表示删除，覆盖这只女仆之前的变更 */
    public void put(UUID owner, UUID maid, Optional<ChunkLoadingData.LevelAndChunkPos> pos) {
        if (!pos.equals(this.changesOf(owner).put(maid, pos))) {
            this.setDirty();
        }
    }

    /** 逐位主人列出待变更，只读 */
    public void forEach(BiConsumer<UUID, Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>>> action) {
        this.changesByOwner.forEach(action);
    }

    /** 取出并清掉这位主人的待变更 */
    public Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> take(UUID owner) {
        Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> changes = this.changesByOwner.remove(owner);
        if (changes == null) {
            return Map.of();
        }
        this.setDirty();
        return changes;
    }

    /** 把一位主人的待变更应用到他的锚定记录上 */
    public static void apply(Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> changes,
                             Map<UUID, ChunkLoadingData.LevelAndChunkPos> records) {
        changes.forEach((maid, pos) -> pos.ifPresentOrElse(p -> records.put(maid, p), () -> records.remove(maid)));
    }

    private Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> changesOf(UUID owner) {
        return this.changesByOwner.computeIfAbsent(owner, key -> new HashMap<>());
    }

    @Nullable
    private static UUID parseUuid(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        CompoundTag changes = new CompoundTag();
        this.changesByOwner.forEach((owner, maids) -> {
            CompoundTag ownerTag = new CompoundTag();
            maids.forEach((maid, pos) -> {
                if (pos.isEmpty()) {
                    ownerTag.put(maid.toString(), new CompoundTag());
                } else {
                    ChunkLoadingData.LevelAndChunkPos.CODEC.encodeStart(NbtOps.INSTANCE, pos.get()).result()
                            .ifPresent(encoded -> ownerTag.put(maid.toString(), encoded));
                }
            });
            changes.put(owner.toString(), ownerTag);
        });
        tag.put("Changes", changes);
        return tag;
    }
}
