package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.api.IPersistentEncounterEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

public final class PersistentEntityLifecycleGuard {
    private PersistentEntityLifecycleGuard() {
    }

    public static boolean shouldBlockRemoval(Entity entity, Entity.RemovalReason reason) {
        if (!(entity instanceof IPersistentEncounterEntity protectedEntity)
                || !protectedEntity.maidspell$shouldProtectLifecycle()) {
            return AnchorCoreProtection.shouldBlockSetRemoved(entity, reason);
        }
        if (BossLifecycleAccess.canRemove(entity, reason)) {
            return false;
        }
        return reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
            && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER;
    }

    public static boolean shouldBlockCapture(Entity entity) {
        return entity instanceof IPersistentEncounterEntity protectedEntity
            && protectedEntity.maidspell$shouldProtectLifecycle()
            || AnchorCoreProtection.shouldBlockCapture(entity);
    }

    public static boolean shouldBlockConversion(Entity entity) {
        return shouldBlockCapture(entity);
    }

    public static boolean conflictsWithLoadedEncounter(Entity candidate, ServerLevel level) {
        if (!(candidate instanceof IPersistentEncounterEntity encounter)) {
            return false;
        }
        // 首版对遭遇唯一性的保证范围是：同一维度、双方都处于已加载状态的实体中，同一个 encounterId 不会出现两个权威实体。
        for (Entity loaded : level.getAllEntities()) {
            if (loaded != candidate && !loaded.isRemoved()
                    && loaded instanceof IPersistentEncounterEntity existing
                    && existing.maidspell$encounterId().equals(encounter.maidspell$encounterId())) {
                return true;
            }
        }
        return false;
    }
}
