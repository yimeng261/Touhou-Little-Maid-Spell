package com.github.yimeng261.maidspell.api;

import net.minecraft.network.syncher.EntityDataAccessor;

public interface IBossSyncedDataGuard {
    boolean maidspell$protectSyncedDataWrite(EntityDataAccessor<?> accessor, Object value);

    /**
     * 同步字段值变更后回调，也覆盖直接修改 {@code DataItem} 再调用 {@code Entity#onSyncedDataUpdated} 的写入，
     * 供实现方让权威状态与网络同步值保持分离
     */
    default void maidspell$onSyncedDataUpdated(EntityDataAccessor<?> accessor, Object value) {
    }
}
