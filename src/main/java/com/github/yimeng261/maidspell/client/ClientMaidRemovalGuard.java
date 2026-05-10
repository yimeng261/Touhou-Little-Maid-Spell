package com.github.yimeng261.maidspell.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientMaidRemovalGuard {
    private static final long MARK_DURATION_MILLIS = 30_000L;
    private static final Map<Integer, Long> PROTECTED_ENTITY_IDS = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> ALLOWED_REMOVAL_IDS = new ConcurrentHashMap<>();

    private ClientMaidRemovalGuard() {
    }

    public static void markProtected(int entityId) {
        PROTECTED_ENTITY_IDS.put(entityId, System.currentTimeMillis() + MARK_DURATION_MILLIS);
    }

    public static void allowRemoval(int entityId) {
        PROTECTED_ENTITY_IDS.remove(entityId);
        ALLOWED_REMOVAL_IDS.put(entityId, System.currentTimeMillis() + MARK_DURATION_MILLIS);
    }

    public static boolean shouldBlockRemoval(Entity entity, int entityId) {
        if (consumeAllowedRemoval(entityId)) {
            return false;
        }

        if (!(entity instanceof EntityMaid maid)) {
            PROTECTED_ENTITY_IDS.remove(entityId);
            return false;
        }

        if (isMarked(entityId)) {
            return true;
        }

        try {
            if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                markProtected(entityId);
                return true;
            }
        } catch (Exception ignored) {
            // Client bauble data can be temporarily unavailable while spawn data is being replayed.
        }
        return false;
    }

    private static boolean consumeAllowedRemoval(int entityId) {
        Long expiresAt = ALLOWED_REMOVAL_IDS.remove(entityId);
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }

    private static boolean isMarked(int entityId) {
        Long expiresAt = PROTECTED_ENTITY_IDS.get(entityId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            PROTECTED_ENTITY_IDS.remove(entityId);
            return false;
        }
        return true;
    }
}
