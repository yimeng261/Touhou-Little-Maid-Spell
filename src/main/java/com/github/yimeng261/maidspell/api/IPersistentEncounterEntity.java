package com.github.yimeng261.maidspell.api;

import net.minecraft.world.entity.Entity;
import java.util.UUID;

public interface IPersistentEncounterEntity {
    boolean maidspell$shouldProtectLifecycle();

    UUID maidspell$encounterId();

    default void maidspell$onBlockedRemoval(Entity.RemovalReason reason) {
    }
}
