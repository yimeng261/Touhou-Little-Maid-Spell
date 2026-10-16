package com.github.yimeng261.maidspell.dimension;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetreatDimensionCompatibilityTest {
    private static final ResourceLocation SHARED = id("the_retreat");
    private static final ResourceLocation FIRST_PRIVATE = id("the_retreat_12345678_1234_5678_9abc_123456789abc");
    private static final ResourceLocation SECOND_PRIVATE = id("the_retreat_87654321_4321_8765_cba9_cba987654321");

    @Test
    void sharedConditionAcceptsSharedAndAnyPlayersPrivateRetreat() {
        assertTrue(TheRetreatDimension.matchesTaskDimension(SHARED, SHARED));
        assertTrue(TheRetreatDimension.matchesTaskDimension(SHARED, FIRST_PRIVATE));
        assertTrue(TheRetreatDimension.matchesTaskDimension(SHARED, SECOND_PRIVATE));
        assertFalse(TheRetreatDimension.isPrivateRetreatDimension(SHARED));
        assertTrue(TheRetreatDimension.isPrivateRetreatDimension(FIRST_PRIVATE));
    }

    @Test
    void explicitPlayerConditionStillMatchesOnlyThatPlayersDimension() {
        assertTrue(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, FIRST_PRIVATE));
        assertFalse(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, SECOND_PRIVATE));
        assertFalse(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, SHARED));
    }

    @Test
    void similarNamesAndForeignNamespacesAreNotRetreatDimensions() {
        for (ResourceLocation location : new ResourceLocation[]{
                id("the_retreat_"), id("the_retreat_backup"), id("the_retreat_old"),
                id("the_retreat_1_2_3_4_5"), id("the_retreat_12345678-1234-5678-9abc-123456789abc"),
                new ResourceLocation("other_mod", "the_retreat"),
                new ResourceLocation("other_mod", FIRST_PRIVATE.getPath()),
                new ResourceLocation("minecraft", "overworld")}) {
            assertFalse(TheRetreatDimension.isRetreatDimension(location), location.toString());
            assertFalse(TheRetreatDimension.matchesTaskDimension(SHARED, location), location.toString());
        }
    }

    @Test
    void ordinaryDimensionConditionsKeepExactMatching() {
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");
        assertTrue(TheRetreatDimension.matchesTaskDimension(overworld, overworld));
        assertFalse(TheRetreatDimension.matchesTaskDimension(overworld, nether));
        assertFalse(TheRetreatDimension.matchesTaskDimension(overworld, FIRST_PRIVATE));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("touhou_little_maid_spell", path);
    }
}
