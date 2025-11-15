package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装有锚点核心的女仆实体不在客户端卸载
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-11-15 00:34
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "removeEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"), cancellable = true)
    public void onRemoveEntity(int entityId, Entity.RemovalReason reason, CallbackInfo ci, @Local Entity entity) {
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            return;
        }
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }
        Minecraft instance = Minecraft.getInstance();
        if (instance.player == null) {
            return;
        }
        if (!instance.player.getUUID().equals(maid.getOwnerUUID())) {
            return;
        }
        if (!AnchoredEntityMaid.isMaidAnchored(maid)) {
            return;
        }
        ci.cancel();
    }
}
