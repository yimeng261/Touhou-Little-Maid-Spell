package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装备锚定核心的女仆传送跳过平滑动画
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-02-23 20:03
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

    @Redirect(method = "handleTeleportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFI)V"))
    private void injectHandleTeleportEntity(Entity entity, double x, double y, double z, float yRot, float xRot, int steps) {
        if (!(entity instanceof EntityMaid maid)) {
            entity.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        ChunkPos chunkPos = maid.chunkPosition();
        ClientLevel clientLevel = (ClientLevel) maid.level();
        if (!clientLevel.getChunkSource().storage.inRange(chunkPos.x, chunkPos.z) && AnchoredEntityMaid.isMaidAnchored(maid)) {
            entity.setPos(x, y, z);
            entity.setYRot(yRot % 360.0F);
            entity.setXRot(xRot % 360.0F);
            return;
        }
        entity.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Inject(
            method = "handleRemoveEntities(Lnet/minecraft/network/protocol/game/ClientboundRemoveEntitiesPacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void maidspell$blockAnchoredMaidClientRemoval(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        IntList entityIds = packet.getEntityIds();
        boolean handledAny = false;

        for (int entityId : entityIds) {
            Entity entity = this.level.getEntity(entityId);
            if (entity != null && ClientMaidRemovalGuard.shouldBlockRemoval(entity, entityId)) {
                handledAny = true;
                Global.LOGGER.debug("[MaidSpell] Blocked client removal packet for protected maid id={} uuid={}",
                        entityId, entity.getUUID());
                continue;
            }

            this.level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
            handledAny = true;
        }

        if (handledAny) {
            ci.cancel();
        }
    }
}
