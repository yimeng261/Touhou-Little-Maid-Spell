package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiMessage;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin用于移除OpenMaidGuiMessage的距离检查限制
 * 允许玩家远程打开女仆GUI（包括饰品栏）
 */
@Mixin(value = OpenMaidGuiMessage.class, remap = false)
public class OpenMaidGuiMessageMixin {
    @Redirect(
            method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/OpenMaidGuiMessage;Lnet/minecraft/server/level/ServerPlayer;)V",
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
    
    /**
     * 注入到 stillValid 方法开头，取消原方法并返回我们的逻辑
     * 移除了距离检查，允许远程打开GUI
     */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void stillValid(Player playerIn, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (maid.getMaidBauble().containsItem(MaidSpellItems.ENDER_POCKET.get())) {
            boolean isValid = maid.isOwnedBy(playerIn) && !maid.isSleeping() && maid.isAlive();
            cir.setReturnValue(isValid);
        }
    }
}
