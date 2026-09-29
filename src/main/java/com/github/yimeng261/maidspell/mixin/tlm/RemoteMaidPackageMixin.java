package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.network.message.MaidConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidModelPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidSubConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidTaskPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.RequestEffectPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SendNameTagPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.ServantBellSetPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SetAttackListPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SetMaidSoundIdPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.ToggleTabPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.YsmMaidModelPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TLM 服务端数据包按实体 ID 找不到女仆时，回退到末影腰包远程会话中的女仆
 */
@Mixin(value = {
        MaidConfigPackage.class,
        MaidModelPackage.class,
        MaidSubConfigPackage.class,
        MaidTaskPackage.class,
        RequestEffectPackage.class,
        SendNameTagPackage.class,
        ServantBellSetPackage.class,
        SetAttackListPackage.class,
        SetMaidSoundIdPackage.class,
        ToggleTabPackage.class,
        YsmMaidModelPackage.class
}, remap = false)
public class RemoteMaidPackageMixin {
    @Redirect(
            method = {
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidConfigPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidModelPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidSubConfigPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/MaidTaskPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/RequestEffectPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/SendNameTagPackage;)V",
                    "lambda$handle$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/github/tartaricacid/touhoulittlemaid/network/message/ServantBellSetPackage;)V",
                    "writeList(Lcom/github/tartaricacid/touhoulittlemaid/network/message/SetAttackListPackage;Lnet/minecraft/world/entity/player/Player;)V",
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
    private static Entity maidspell$resolveRemoteMaid(Level level, int entityId) {
        return EnderPocketService.resolvePacketEntity(level, entityId);
    }
}
