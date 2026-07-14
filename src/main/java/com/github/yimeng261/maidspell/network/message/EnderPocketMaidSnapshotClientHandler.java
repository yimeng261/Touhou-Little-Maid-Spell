package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketMaidProxyCache;
import com.github.yimeng261.maidspell.mixin.accessor.EntityInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class EnderPocketMaidSnapshotClientHandler {
    private EnderPocketMaidSnapshotClientHandler() {
    }

    static void handle(S2CEnderPocketMaidSnapshot message) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        MaidEntityRestoreMessage snapshot = message.snapshot();
        Entity entity = MaidEntityRestoreClientHandler.createEntity(level, snapshot);
        if (!(entity instanceof EntityMaid maid)) {
            Global.LOGGER.warn("[MaidSpell] Rejected non-maid Ender Pocket proxy entity type={}",
                    snapshot.entityTypeId());
            return;
        }

        ((EntityInvoker) maid).maidspell$invokeUnsetRemoved();
        if (!snapshot.entityData().isEmpty()) {
            maid.getEntityData().assignValues(snapshot.entityData());
        }
        maid.moveTo(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yRot(), snapshot.xRot());
        maid.setInvisible(false);
        maid.refreshDimensions();
        EnderPocketMaidProxyCache.store(level, maid);

        Global.LOGGER.debug("[MaidSpell] Synchronized Ender Pocket maid proxy uuid={} entityId={}",
                maid.getUUID(), maid.getId());
        if (message.acknowledge() && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new C2SEnderPocketMaidReady(message.sessionId()));
        }
    }
}
