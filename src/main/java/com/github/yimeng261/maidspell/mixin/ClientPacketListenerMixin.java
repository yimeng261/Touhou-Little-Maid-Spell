package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 装备锚定核心的女仆传送跳过平滑动画
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-02-23 20:03
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Redirect(method = "handleTeleportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFI)V"))
    private void injectHandleTeleportEntity(Entity entity, double x, double y, double z, float yRot, float xRot, int steps) {
        if (!(entity instanceof EntityMaid maid)) {
            entity.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        ChunkPos chunkPos = maid.chunkPosition();
        ClientLevel level = (ClientLevel) maid.level();
        if (!level.getChunkSource().storage.inRange(chunkPos.x, chunkPos.z) && AnchoredEntityMaid.isMaidAnchored(maid)) {
            entity.setPos(x, y, z);
            entity.setYRot(yRot % 360.0F);
            entity.setXRot(xRot % 360.0F);
            return;
        }
        entity.lerpTo(x, y, z, yRot, xRot, steps);
    }
}
