package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.network.message.SetAttackListPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 攻击名单数据包按实体 ID 找不到女仆时，回退到发送者自己的末影腰包远程会话中的女仆
 */
@Mixin(value = SetAttackListPackage.class, remap = false)
public class SetAttackListPackageMixin {
    @WrapOperation(
            method = "writeList(Lcom/github/tartaricacid/touhoulittlemaid/network/message/SetAttackListPackage;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                    remap = true
            ),
            remap = true
    )
    private static Entity maidspell$resolveRemoteMaid(Level level, int entityId, Operation<Entity> original,
                                                      @Local(argsOnly = true) Player sender) {
        return EnderPocketService.resolvePacketEntity(original.call(level, entityId), entityId, sender, true);
    }
}
