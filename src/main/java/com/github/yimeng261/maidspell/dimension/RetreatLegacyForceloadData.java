package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 按维度记录旧版寻风之铃强加载残留是否已经清理过。
 * 存在该维度自己的 data/ 目录，和原版强加载列表一起落盘；维度被删掉重建时两者一起消失。
 */
public class RetreatLegacyForceloadData extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_retreat_legacy_forceload";

    private boolean released;

    private RetreatLegacyForceloadData() {
    }

    private static RetreatLegacyForceloadData load(CompoundTag tag, HolderLookup.Provider registries) {
        RetreatLegacyForceloadData data = new RetreatLegacyForceloadData();
        data.released = tag.getBoolean("Released");
        return data;
    }

    public static RetreatLegacyForceloadData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RetreatLegacyForceloadData::new, RetreatLegacyForceloadData::load),
                DATA_NAME);
    }

    public boolean isReleased() {
        return this.released;
    }

    public void markReleased() {
        this.released = true;
        this.setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        tag.putBoolean("Released", this.released);
        return tag;
    }
}
