package com.github.yimeng261.maidspell.winefox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** One spell copied from an Iron's Spellbooks scroll. */
public record WinefoxSpellChoice(String id, int level) {
    public static final int MAX_SLOTS_PER_PHASE = 27;

    public static WinefoxSpellChoice valid(String id, int level) {
        return id != null && id.length() <= 128 && ResourceLocation.tryParse(id) != null
                && level >= 1 && level <= 10 ? new WinefoxSpellChoice(id, level) : null;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putInt("Level", level);
        return tag;
    }

    public static WinefoxSpellChoice fromTag(CompoundTag tag) {
        return valid(tag.getString("Id"), tag.getInt("Level"));
    }
}
