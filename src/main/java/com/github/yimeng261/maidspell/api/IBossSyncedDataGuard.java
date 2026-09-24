package com.github.yimeng261.maidspell.api;

import net.minecraft.network.syncher.EntityDataAccessor;

public interface IBossSyncedDataGuard {
    boolean maidspell$protectSyncedDataWrite(EntityDataAccessor<?> accessor, Object value);
}
