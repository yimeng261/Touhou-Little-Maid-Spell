package com.github.yimeng261.maidspell.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 存档级的隐世女仆认领记录，不依赖会被丢弃重建的归隐之地维度数据。 */
public final class HiddenRetreatMaidData extends SavedData {
    public static final String DATA_NAME = "touhou_little_maid_spell_retreat_maid_claims";
    private final Set<UUID> claimedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSpawns = ConcurrentHashMap.newKeySet();
    // 不可变快照：结构装饰线程只读，写入只在服务端主线程。
    private volatile List<StructureOwner> structureOwners = List.of();

    public boolean hasClaim(UUID player) {
        return claimedPlayers.contains(player);
    }

    /** 只在服务端主线程调用。女仆卸载、死亡、转成物品或导出都不会撤销认领。 */
    public boolean claim(UUID player) {
        if (!claimedPlayers.add(player)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** 管理员显式重置；已有的女仆和建筑不会被移除。 */
    public boolean reset(UUID player) {
        if (!claimedPlayers.remove(player)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** 短时的创建许可，不会作为不可撤销的认领落盘。 */
    public boolean tryBeginSpawn(UUID player) {
        if (hasClaim(player) || !pendingSpawns.add(player)) {
            return false;
        }
        if (hasClaim(player)) {
            pendingSpawns.remove(player);
            return false;
        }
        return true;
    }

    public void finishSpawn(UUID player) {
        pendingSpawns.remove(player);
    }

    public record StructureOwner(ResourceLocation dimension, int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ, UUID player) {
        boolean contains(ResourceLocation dimension, BlockPos pos) {
            return this.dimension.equals(dimension) && pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        boolean sameStructure(StructureOwner other) {
            return dimension.equals(other.dimension) && minX == other.minX && minY == other.minY
                    && minZ == other.minZ && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
        }
    }

    /** 在拉起结构区块之前绑定归属，之后再次搜索也不能改派同一座结构。 */
    public void bindStructure(StructureOwner owner) {
        if (structureOwners.stream().anyMatch(existing -> existing.sameStructure(owner))) {
            return;
        }
        List<StructureOwner> updated = new ArrayList<>(structureOwners);
        updated.add(owner);
        structureOwners = List.copyOf(updated);
        setDirty();
    }

    @Nullable
    public UUID ownerAt(ResourceLocation dimension, BlockPos pos) {
        for (StructureOwner owner : structureOwners) {
            if (owner.contains(dimension, pos)) {
                return owner.player;
            }
        }
        return null;
    }

    public static HiddenRetreatMaidData load(CompoundTag tag, HolderLookup.Provider registries) {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        for (Tag element : tag.getList("Claims", Tag.TAG_COMPOUND)) {
            CompoundTag claim = (CompoundTag) element;
            if (claim.hasUUID("Player")) {
                data.claimedPlayers.add(claim.getUUID("Player"));
            }
        }
        List<StructureOwner> owners = new ArrayList<>();
        for (Tag element : tag.getList("Structures", Tag.TAG_COMPOUND)) {
            CompoundTag structure = (CompoundTag) element;
            ResourceLocation dimension = ResourceLocation.tryParse(structure.getString("Dimension"));
            int[] box = structure.getIntArray("Bounds");
            if (dimension != null && structure.hasUUID("Player") && box.length == 6
                    && box[0] <= box[3] && box[1] <= box[4] && box[2] <= box[5]) {
                owners.add(new StructureOwner(dimension, box[0], box[1], box[2], box[3], box[4], box[5],
                        structure.getUUID("Player")));
            }
        }
        data.structureOwners = List.copyOf(owners);
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        ListTag claims = new ListTag();
        for (UUID player : claimedPlayers) {
            CompoundTag claim = new CompoundTag();
            claim.putUUID("Player", player);
            claims.add(claim);
        }
        tag.put("Claims", claims);
        ListTag structures = new ListTag();
        for (StructureOwner owner : structureOwners) {
            CompoundTag structure = new CompoundTag();
            structure.putString("Dimension", owner.dimension.toString());
            structure.putUUID("Player", owner.player);
            structure.putIntArray("Bounds", new int[]{owner.minX, owner.minY, owner.minZ,
                    owner.maxX, owner.maxY, owner.maxZ});
            structures.add(structure);
        }
        tag.put("Structures", structures);
        return tag;
    }
}
