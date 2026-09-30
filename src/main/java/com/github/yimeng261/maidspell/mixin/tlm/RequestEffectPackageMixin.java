package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.network.message.RequestEffectPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TLM 查询女仆药水效果时按实体 ID 找不到女仆，回退到发送者自己的末影腰包远程会话中的女仆。
 * 查询只读女仆，不触发同步数据推送。
 */
@Mixin(value = RequestEffectPackage.class, remap = false)
public class RequestEffectPackageMixin {
    @WrapOperation(
            method = "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/RequestEffectPackage;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                    remap = true
            ),
            remap = true
    )
    private static Entity maidspell$resolveRemoteMaid(Level level, int entityId, Operation<Entity> original,
                                                      @Local(argsOnly = true) IPayloadContext context) {
        return EnderPocketService.resolvePacketEntity(original.call(level, entityId), entityId, context.player(), false);
    }
}
