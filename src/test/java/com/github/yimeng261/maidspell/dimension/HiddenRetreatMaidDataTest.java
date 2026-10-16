package com.github.yimeng261.maidspell.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HiddenRetreatMaidDataTest {
    private static final UUID FIRST = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final UUID SECOND = UUID.fromString("87654321-4321-8765-cba9-cba987654321");
    private static final ResourceLocation DIMENSION = new ResourceLocation("touhou_little_maid_spell", "the_retreat");

    @Test
    void claimsSurviveRestartAndDoNotAffectOtherPlayers() {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        assertTrue(data.claim(FIRST));
        assertFalse(data.claim(FIRST));
        HiddenRetreatMaidData loaded = HiddenRetreatMaidData.load(data.save(new CompoundTag()));
        assertTrue(loaded.hasClaim(FIRST));
        assertFalse(loaded.hasClaim(SECOND));
        assertFalse(loaded.tryBeginSpawn(FIRST));
        assertTrue(loaded.tryBeginSpawn(SECOND));
        loaded.finishSpawn(SECOND);
        assertTrue(loaded.reset(FIRST));
        assertTrue(loaded.tryBeginSpawn(FIRST));
    }

    @Test
    void constructionFailureReleasesPermitAndPendingIsNeverSavedAsClaim() {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        assertTrue(data.tryBeginSpawn(FIRST));
        assertFalse(data.tryBeginSpawn(FIRST));
        HiddenRetreatMaidData restarted = HiddenRetreatMaidData.load(data.save(new CompoundTag()));
        assertTrue(restarted.tryBeginSpawn(FIRST));
        data.finishSpawn(FIRST);
        assertTrue(data.tryBeginSpawn(FIRST));
        data.claim(FIRST);
        data.finishSpawn(FIRST);
        assertFalse(data.tryBeginSpawn(FIRST));
    }

    @Test
    void sharedStructureOwnershipIsPersistentAndCannotBeReassigned() {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        data.bindStructure(owner(FIRST));
        data.bindStructure(owner(SECOND));
        HiddenRetreatMaidData loaded = HiddenRetreatMaidData.load(data.save(new CompoundTag()));
        assertEquals(FIRST, loaded.ownerAt(DIMENSION, new BlockPos(5, 70, 5)));
        assertNull(loaded.ownerAt(DIMENSION, new BlockPos(11, 70, 5)));
        assertNull(loaded.ownerAt(new ResourceLocation("other", "the_retreat"), new BlockPos(5, 70, 5)));
    }

    private static HiddenRetreatMaidData.StructureOwner owner(UUID player) {
        return new HiddenRetreatMaidData.StructureOwner(DIMENSION, 0, 60, 0, 10, 80, 10, player);
    }
}
