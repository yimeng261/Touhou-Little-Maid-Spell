package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.network.message.MaidConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidModelPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidSubConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidTaskPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SendNameTagPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.ServantBellSetPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SetMaidSoundIdPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.ToggleTabPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.YsmMaidModelPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TLM 服务端数据包按实体 ID 找不到女仆时，回退到发送者自己的末影腰包远程会话中的女仆
 */
@Mixin(value = {
        MaidConfigPackage.class,
        MaidModelPackage.class,
        MaidSubConfigPackage.class,
        MaidTaskPackage.class,
        SendNameTagPackage.class,
        ServantBellSetPackage.class,
        SetMaidSoundIdPackage.class,
        ToggleTabPackage.class,
        YsmMaidModelPackage.class
}, remap = false)
public class RemoteMaidPackageMixin {
    @WrapOperation(
            method = {
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidConfigPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidModelPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidSubConfigPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidTaskPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/SendNameTagPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/ServantBellSetPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/SetMaidSoundIdPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/ToggleTabPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/YsmMaidModelPackage;)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                    remap = true
            ),
            remap = true
    )
    private static Entity maidspell$resolveRemoteMaid(Level level, int entityId, Operation<Entity> original,
                                                      @Local(argsOnly = true) IPayloadContext context) {
        return EnderPocketService.resolvePacketEntity(original.call(level, entityId), entityId, context.player(), true);
    }
}
