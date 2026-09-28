package com.github.yimeng261.maidspell.winefox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Three named-by-position presets retained on the dagger between challenges. */
public final class WinefoxChallengePresets {
    public static final int COUNT = 3;
    private static final String TAG = "MaidSpellWinefoxPresets";

    private WinefoxChallengePresets() {
    }

    public static List<WinefoxChallengeConfig> read(ItemStack stack) {
        List<WinefoxChallengeConfig> result = empty();
        CompoundTag root = stack.getTag();
        if (root == null) return result;
        ListTag list = root.getList(TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(COUNT, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("Config", Tag.TAG_COMPOUND)) {
                result.set(i, WinefoxChallengeConfig.fromTag(entry.getCompound("Config")));
            }
        }
        return result;
    }

    public static void write(ItemStack stack, List<WinefoxChallengeConfig> presets) {
        ListTag list = new ListTag();
        for (int i = 0; i < COUNT; i++) {
            CompoundTag entry = new CompoundTag();
            if (i < presets.size() && presets.get(i) != null) {
                entry.put("Config", presets.get(i).sanitized().toTag());
            }
            list.add(entry);
        }
        stack.getOrCreateTag().put(TAG, list);
    }

    public static List<WinefoxChallengeConfig> empty() {
        List<WinefoxChallengeConfig> result = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) result.add(null);
        return result;
    }

    public static void writeBuffer(FriendlyByteBuf buffer, List<WinefoxChallengeConfig> presets) {
        for (int i = 0; i < COUNT; i++) {
            WinefoxChallengeConfig preset = i < presets.size() ? presets.get(i) : null;
            buffer.writeBoolean(preset != null);
            if (preset != null) preset.writeToBuffer(buffer);
        }
    }

    public static List<WinefoxChallengeConfig> readBuffer(FriendlyByteBuf buffer) {
        List<WinefoxChallengeConfig> result = empty();
        for (int i = 0; i < COUNT; i++) {
            if (buffer.readBoolean()) result.set(i, WinefoxChallengeConfig.fromBuffer(buffer));
        }
        return result;
    }

    public static List<WinefoxChallengeConfig> readRawBuffer(FriendlyByteBuf buffer) {
        List<WinefoxChallengeConfig> result = empty();
        for (int i = 0; i < COUNT; i++) {
            if (buffer.readBoolean()) result.set(i, WinefoxChallengeConfig.readRawFromBuffer(buffer));
        }
        return result;
    }
}
