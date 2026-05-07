package com.github.yimeng261.maidspell.mixin.client;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import it.unimi.dsi.fastutil.ints.IntList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

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
        boolean blockedAny = false;
        boolean removedAny = false;

        for (int entityId : entityIds) {
            Entity entity = this.level.getEntity(entityId);
            if (entity != null && ClientMaidRemovalGuard.shouldBlockRemoval(entity, entityId)) {
                blockedAny = true;
                Global.LOGGER.debug("[MaidSpell] Blocked client removal packet for protected maid id={} uuid={}",
                        entityId, entity.getUUID());
                continue;
            }

            this.level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
            removedAny = true;
        }

        if (blockedAny || removedAny) {
            ci.cancel();
        }
    }
}
