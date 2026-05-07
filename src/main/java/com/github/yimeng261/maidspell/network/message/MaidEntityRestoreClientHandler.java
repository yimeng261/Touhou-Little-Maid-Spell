package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.mixin.accessor.EntityInvoker;
import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

final class MaidEntityRestoreClientHandler {
    private MaidEntityRestoreClientHandler() {
    }

    static void handle(MaidEntityRestoreMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        ClientLevel level = minecraft.level;
        ClientMaidRemovalGuard.markProtected(message.entityId());
        Entity entity = level.getEntity(message.entityId());
        if (entity == null) {
            entity = createEntity(level, message);
            if (entity == null) {
                return;
            }
            level.putNonPlayerEntity(message.entityId(), entity);
        }

        ((EntityInvoker) entity).maidspell$invokeUnsetRemoved();
        if (!message.entityData().isEmpty()) {
            entity.getEntityData().assignValues(message.entityData());
        }
        entity.moveTo(message.x(), message.y(), message.z(), message.yRot(), message.xRot());
        entity.setInvisible(false);
        entity.refreshDimensions();
    }

    private static Entity createEntity(ClientLevel level, MaidEntityRestoreMessage message) {
        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(message.entityTypeId());
        if (entityType.isEmpty()) {
            return null;
        }

        Entity entity = entityType.get().create(level);
        if (entity == null) {
            return null;
        }

        entity.setId(message.entityId());
        entity.setUUID(message.uuid());
        if (!message.entityTag().isEmpty()) {
            entity.load(message.entityTag());
            entity.setId(message.entityId());
            entity.setUUID(message.uuid());
        }
        entity.moveTo(message.x(), message.y(), message.z(), message.yRot(), message.xRot());
        return entity;
    }
}
