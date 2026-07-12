package com.github.yimeng261.maidspell.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMaidRemovalGuard {
    private static final long MARK_DURATION_MILLIS = 30_000L;
    private static final Map<Integer, Long> PROTECTED_ENTITY_IDS = new ConcurrentHashMap<>();

    private ClientMaidRemovalGuard() {
    }

    public static void markProtected(int entityId) {
        PROTECTED_ENTITY_IDS.put(entityId, System.currentTimeMillis() + MARK_DURATION_MILLIS);
    }

    public static void allowRemovalNow(int entityId) {
        PROTECTED_ENTITY_IDS.remove(entityId);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(entityId) != null) {
            level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
        }
    }

    public static boolean shouldBlockRemoval(Entity entity, int entityId) {
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

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            PROTECTED_ENTITY_IDS.clear();
        }
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
