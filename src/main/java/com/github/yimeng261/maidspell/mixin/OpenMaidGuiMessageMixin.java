package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiMessage;
import com.github.yimeng261.maidspell.Global;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin用于移除打开女仆GUI消息处理中的距离检查限制
 * 允许玩家在任意距离通过网络消息打开女仆界面
 */
@Mixin(value = OpenMaidGuiMessage.class, remap = false)
public class OpenMaidGuiMessageMixin {

    /**
     * 注入到 stillValid 方法开头，取消原方法并返回我们的逻辑
     */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void stillValid(Player playerIn, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        boolean isValid = maid.isOwnedBy(playerIn) && !maid.isSleeping() && maid.isAlive();
        cir.setReturnValue(isValid);
    }
}
