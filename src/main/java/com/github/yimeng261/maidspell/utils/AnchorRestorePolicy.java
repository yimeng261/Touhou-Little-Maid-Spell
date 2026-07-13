package com.github.yimeng261.maidspell.utils;

final class AnchorRestorePolicy {
    enum Decision {
        RETRY,
        DISCARD,
        CONTINUE
    }

    enum RestorePhase {
        WAITING_CHUNK,
        WAITING_ENTITIES
    }

    enum RestoreAction {
        WAIT,
        DISCARD,
        COMMIT
    }

    record RestoreStep(RestorePhase phase, RestoreAction action) {
    }

    private AnchorRestorePolicy() {
    }

    static Decision forDimension(boolean available) {
        return available ? Decision.CONTINUE : Decision.RETRY;
    }

    static Decision forEntity(boolean storageLoaded, boolean present, boolean valid) {
        if (!storageLoaded) {
            return Decision.RETRY;
        }
        return present && valid ? Decision.CONTINUE : Decision.DISCARD;
    }

    static RestoreStep forRestore(boolean chunkLoaded, boolean storageLoaded,
                                  boolean present, boolean valid) {
        if (!chunkLoaded) {
            return new RestoreStep(RestorePhase.WAITING_CHUNK, RestoreAction.WAIT);
        }
        if (!storageLoaded) {
            return new RestoreStep(RestorePhase.WAITING_ENTITIES, RestoreAction.WAIT);
        }
        return new RestoreStep(
                RestorePhase.WAITING_ENTITIES,
                present && valid ? RestoreAction.COMMIT : RestoreAction.DISCARD);
    }
}
