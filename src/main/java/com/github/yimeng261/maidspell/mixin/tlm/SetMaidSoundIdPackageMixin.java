package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.network.message.SetMaidSoundIdPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 按实体 ID 找不到女仆时，回退到末影腰包远程会话中的女仆
 */
@Mixin(value = SetMaidSoundIdPackage.class, remap = false)
public class SetMaidSoundIdPackageMixin {
    @Redirect(
            method = "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/SetMaidSoundIdPackage;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                    remap = true
            ),
            remap = true
    )
    private static Entity maidspell$resolveRemoteMaid(Level level, int entityId) {
        return EnderPocketService.resolvePacketEntity(level, entityId);
    }
}
