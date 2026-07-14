package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.SendEffectPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketMaidProxyCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 客户端找不到女仆实体时，回退到末影腰包远程界面使用的女仆代理
 */
@Mixin(value = SendEffectPackage.class, remap = false)
public class SendEffectPackageMixin {
    @Redirect(
            method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/SendEffectPackage;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                    remap = true
            ),
            remap = true
    )
    private static Entity maidspell$resolveRemoteMaid(ClientLevel level, int entityId) {
        Entity local = level.getEntity(entityId);
        return local instanceof EntityMaid ? local : EnderPocketMaidProxyCache.find(level, entityId);
    }
}
