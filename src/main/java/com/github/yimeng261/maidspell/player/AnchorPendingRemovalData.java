package com.github.yimeng261.maidspell.player;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 主人不在线时要删掉的锚定记录。
 * <p>
 * 锚定记录存在主人身上，主人离线时改不了；女仆这时死亡、被移除或卸下锚定核心，先记在这里，
 * 主人登录时从他的记录里删掉。存在主世界的 data/ 目录。
 */
public class AnchorPendingRemovalData extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_anchor_pending_removals";

    private final Map<UUID, Set<UUID>> maidsByOwner = new HashMap<>();

    private AnchorPendingRemovalData() {
    }

    private static AnchorPendingRemovalData load(CompoundTag tag, HolderLookup.Provider registries) {
        AnchorPendingRemovalData data = new AnchorPendingRemovalData();
        CompoundTag owners = tag.getCompound("Owners");
        for (String ownerKey : owners.getAllKeys()) {
            UUID owner;
            try {
                owner = UUID.fromString(ownerKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Set<UUID> maids = new HashSet<>();
            for (Tag maidTag : owners.getList(ownerKey, Tag.TAG_INT_ARRAY)) {
                maids.add(NbtUtils.loadUUID(maidTag));
            }
            if (!maids.isEmpty()) {
                data.maidsByOwner.put(owner, maids);
            }
        }
        return data;
    }

    public static AnchorPendingRemovalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AnchorPendingRemovalData::new, AnchorPendingRemovalData::load),
                DATA_NAME);
    }

    public void add(UUID owner, UUID maid) {
        if (this.maidsByOwner.computeIfAbsent(owner, key -> new HashSet<>()).add(maid)) {
            this.setDirty();
        }
    }

    /** 逐位主人列出待删记录，只读 */
    public void forEach(BiConsumer<UUID, Set<UUID>> action) {
        this.maidsByOwner.forEach(action);
    }

    /** 取出并清掉这位主人的待删记录 */
    public Set<UUID> take(UUID owner) {
        Set<UUID> maids = this.maidsByOwner.remove(owner);
        if (maids == null) {
            return Set.of();
        }
        this.setDirty();
        return maids;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        CompoundTag owners = new CompoundTag();
        this.maidsByOwner.forEach((owner, maids) -> {
            ListTag list = new ListTag();
            maids.forEach(maid -> list.add(NbtUtils.createUUID(maid)));
            owners.put(owner.toString(), list);
        });
        tag.put("Owners", owners);
        return tag;
    }
}
