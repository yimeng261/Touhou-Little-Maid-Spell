package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.network.message.RequestEffectMessage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RequestEffectMessage.class, remap = false)
public class RequestEffectMessageMixin {
    @Redirect(method = "lambda$handle$0(Lnet/minecraftforge/network/NetworkEvent$Context;Lcom/github/tartaricacid/touhoulittlemaid/network/message/RequestEffectMessage;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;", remap = true), remap = true)
    private static Entity maidspell$resolveRemoteMaid(Level level, int entityId) {
        return EnderPocketService.resolvePacketEntity(level, entityId);
    }
}
