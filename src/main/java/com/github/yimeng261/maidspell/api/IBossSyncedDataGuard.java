package com.github.yimeng261.maidspell.api;

import net.minecraft.network.syncher.EntityDataAccessor;

public interface IBossSyncedDataGuard {
    boolean maidspell$protectSyncedDataWrite(EntityDataAccessor<?> accessor, Object value);

    /**
     * Observe a synced-data update after the backing value has changed.
     *
     * <p>This callback also covers writers that mutate a {@code DataItem}
     * directly and then invoke {@code Entity#onSyncedDataUpdated}, so custom
     * health implementations can keep their authoritative state separate from
     * the network mirror.</p>
     */
    default void maidspell$onSyncedDataUpdated(EntityDataAccessor<?> accessor, Object value) {
    }
}
