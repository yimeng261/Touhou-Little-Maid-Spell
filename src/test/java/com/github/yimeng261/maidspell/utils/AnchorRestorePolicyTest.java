package com.github.yimeng261.maidspell.utils;

import org.junit.jupiter.api.Test;

import static com.github.yimeng261.maidspell.utils.AnchorRestorePolicy.Decision.CONTINUE;
import static com.github.yimeng261.maidspell.utils.AnchorRestorePolicy.Decision.DISCARD;
import static com.github.yimeng261.maidspell.utils.AnchorRestorePolicy.Decision.RETRY;
import static com.github.yimeng261.maidspell.utils.AnchorRestorePolicy.RestoreAction.COMMIT;
import static com.github.yimeng261.maidspell.utils.AnchorRestorePolicy.RestoreAction.WAIT;
import static com.github.yimeng261.maidspell.utils.AnchorRestorePolicy.RestorePhase.WAITING_CHUNK;
import static com.github.yimeng261.maidspell.utils.AnchorRestorePolicy.RestorePhase.WAITING_ENTITIES;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnchorRestorePolicyTest {
    @Test
    void unavailableDimensionIsTransient() {
        assertEquals(RETRY, AnchorRestorePolicy.forDimension(false));
        assertEquals(CONTINUE, AnchorRestorePolicy.forDimension(true));
    }

    @Test
    void incompleteEntityStorageWinsOverEntityAbsence() {
        assertEquals(RETRY, AnchorRestorePolicy.forEntity(false, false, false));
    }

    @Test
    void missingEntityCanBeDiscardedAfterStorageLoads() {
        assertEquals(DISCARD, AnchorRestorePolicy.forEntity(true, false, false));
    }

    @Test
    void explicitlyInvalidMaidStateCanBeDiscarded() {
        assertEquals(DISCARD, AnchorRestorePolicy.forEntity(true, true, false));
        assertEquals(CONTINUE, AnchorRestorePolicy.forEntity(true, true, true));
    }

    @Test
    void unloadedChunkRemainsInChunkPhase() {
        assertEquals(
                new AnchorRestorePolicy.RestoreStep(WAITING_CHUNK, WAIT),
                AnchorRestorePolicy.forRestore(false, false, false, false));
    }

    @Test
    void loadedChunkWaitsForEntityStorage() {
        assertEquals(
                new AnchorRestorePolicy.RestoreStep(WAITING_ENTITIES, WAIT),
                AnchorRestorePolicy.forRestore(true, false, false, false));
    }

    @Test
    void loadedEntityStorageMakesATerminalDecision() {
        assertEquals(
                new AnchorRestorePolicy.RestoreStep(
                        WAITING_ENTITIES, AnchorRestorePolicy.RestoreAction.DISCARD),
                AnchorRestorePolicy.forRestore(true, true, false, false));
        assertEquals(
                new AnchorRestorePolicy.RestoreStep(
                        WAITING_ENTITIES, AnchorRestorePolicy.RestoreAction.DISCARD),
                AnchorRestorePolicy.forRestore(true, true, true, false));
        assertEquals(
                new AnchorRestorePolicy.RestoreStep(WAITING_ENTITIES, COMMIT),
                AnchorRestorePolicy.forRestore(true, true, true, true));
    }

    @Test
    void restoreDecisionPrioritiesHoldForEveryBooleanCombination() {
        boolean[] values = {false, true};
        for (boolean chunkLoaded : values) {
            for (boolean storageLoaded : values) {
                for (boolean present : values) {
                    for (boolean valid : values) {
                        AnchorRestorePolicy.RestoreStep actual = AnchorRestorePolicy.forRestore(
                                chunkLoaded, storageLoaded, present, valid);
                        AnchorRestorePolicy.RestoreStep expected;
                        if (!chunkLoaded) {
                            expected = new AnchorRestorePolicy.RestoreStep(WAITING_CHUNK, WAIT);
                        } else if (!storageLoaded) {
                            expected = new AnchorRestorePolicy.RestoreStep(WAITING_ENTITIES, WAIT);
                        } else {
                            expected = new AnchorRestorePolicy.RestoreStep(
                                    WAITING_ENTITIES,
                                    present && valid
                                            ? COMMIT
                                            : AnchorRestorePolicy.RestoreAction.DISCARD);
                        }
                        assertEquals(expected, actual,
                                () -> "chunk=" + chunkLoaded + ", storage=" + storageLoaded
                                        + ", present=" + present + ", valid=" + valid);
                    }
                }
            }
        }
    }
}
