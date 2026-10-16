package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaidOriginDataTest {
    @Test
    void transportPreservesOtherForgeDataAndCopiesFutureOriginFields() {
        CompoundTag persistent = persistent(true);
        persistent.getCompound(MaidOriginData.ORIGIN).putString("FutureField", "retained");
        CompoundTag data = new CompoundTag();
        CompoundTag forgeData = new CompoundTag();
        forgeData.putString("OtherMod", "keep");
        data.put("ForgeData", forgeData);
        MaidOriginData.copyToTransport(persistent, data);
        assertEquals("keep", data.getCompound("ForgeData").getString("OtherMod"));
        assertEquals("retained", MaidOriginData.getOrigin(data).getString("FutureField"));
        persistent.getCompound(MaidOriginData.ORIGIN).putString("FutureField", "changed");
        assertEquals("retained", MaidOriginData.getOrigin(data).getString("FutureField"));
        CompoundTag restored = new CompoundTag();
        MaidOriginData.restoreTransportData(data, restored);
        assertTrue(MaidOriginData.preservesPermanentEffects(restored));
        assertEquals(data.getCompound(MaidOriginData.ORIGIN), restored.getCompound(MaidOriginData.ORIGIN));
    }

    @Test
    void legacyRootOnlyMarkersSurviveSubsequentEntityLoad() {
        CompoundTag data = new CompoundTag();
        data.putBoolean(MaidOriginData.HIDDEN_RETREAT_MAID, true);
        data.putBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED, true);
        MaidOriginData.restoreTransportData(data, new CompoundTag());
        CompoundTag loadedPersistent = data.getCompound("ForgeData");
        assertTrue(loadedPersistent.getBoolean(MaidOriginData.HIDDEN_RETREAT_MAID));
        assertTrue(loadedPersistent.getBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED));
        assertTrue(MaidOriginData.isHiddenRetreatMaid(loadedPersistent));
        assertEquals(MaidOriginData.HIDDEN_RETREAT_SOURCE, MaidOriginData.getOrigin(data).getString("Source"));
        assertFalse(MaidOriginData.preservesPermanentEffects(data));
    }

    @Test
    void filmRestoresOnlyInfiniteEffectsAndLeavesSoulCharmPayloadIntact() {
        CompoundTag permanent = new CompoundTag();
        permanent.putInt("Duration", -1);
        permanent.putString("forge:id", "future_mod:effect");
        permanent.putByte("Amplifier", (byte) 4);
        CompoundTag finite = new CompoundTag();
        finite.putInt("Duration", 1200);
        ListTag effects = new ListTag();
        effects.add(permanent);
        effects.add(finite);
        CompoundTag film = new CompoundTag();
        MaidOriginData.restorePermanentEffects(persistent(true), effects, film);
        assertEquals(1, film.getList("ActiveEffects", Tag.TAG_COMPOUND).size());
        assertEquals(permanent, film.getList("ActiveEffects", Tag.TAG_COMPOUND).getCompound(0));
        CompoundTag ordinaryFilm = new CompoundTag();
        MaidOriginData.restorePermanentEffects(new CompoundTag(), effects, ordinaryFilm);
        assertFalse(ordinaryFilm.contains("ActiveEffects"));
        CompoundTag charm = new CompoundTag();
        charm.put("ActiveEffects", effects.copy());
        MaidOriginData.restorePermanentEffects(persistent(true), effects, charm);
        assertEquals(effects, charm.get("ActiveEffects"));
    }

    @Test
    void originAloneDoesNotGrantEffectImportPermission() {
        assertFalse(MaidOriginData.preservesPermanentEffects(persistent(false)));
        assertFalse(MaidOriginData.isHiddenRetreatMaid(persistent(true)));
        CompoundTag invalid = persistent(true);
        invalid.getCompound(MaidOriginData.ORIGIN).putString("Source", "invalid source");
        assertFalse(MaidOriginData.preservesPermanentEffects(invalid));
    }

    private static CompoundTag persistent(boolean preserve) {
        CompoundTag tag = new CompoundTag();
        tag.put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin("touhou_little_maid_spell:fallen_sanctum", preserve));
        return tag;
    }
}
