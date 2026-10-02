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
        // 只在同一维度的已加载实体中检查 encounterId 是否重复
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
