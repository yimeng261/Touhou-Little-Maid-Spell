package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketMaidProxyCache;
import com.github.yimeng261.maidspell.mixin.accessor.EntityInvoker;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

final class EnderPocketMaidSnapshotClientHandler {
    private EnderPocketMaidSnapshotClientHandler() {
    }

    static void handle(EnderPocketMaidSnapshotMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Entity entity = MaidEntityRestoreClientHandler.createEntity(level, message.snapshot());
        if (!(entity instanceof EntityMaid maid)) {
            Global.LOGGER.warn("[MaidSpell] Rejected non-maid Ender Pocket proxy entity type={}",
                    message.snapshot().entityTypeId());
            return;
        }

        ((EntityInvoker) maid).maidspell$invokeUnsetRemoved();
        if (!message.snapshot().entityData().isEmpty()) {
            maid.getEntityData().assignValues(message.snapshot().entityData());
        }
        maid.moveTo(message.snapshot().x(), message.snapshot().y(), message.snapshot().z(),
                message.snapshot().yRot(), message.snapshot().xRot());
        maid.setInvisible(false);
        maid.refreshDimensions();
        EnderPocketMaidProxyCache.store(level, maid);

        Global.LOGGER.debug("[MaidSpell] Synchronized Ender Pocket maid proxy uuid={} entityId={}",
                maid.getUUID(), maid.getId());
        if (message.acknowledge()) {
            NetworkHandler.CHANNEL.sendToServer(new EnderPocketMaidReadyMessage(message.sessionId()));
        }
    }
}
