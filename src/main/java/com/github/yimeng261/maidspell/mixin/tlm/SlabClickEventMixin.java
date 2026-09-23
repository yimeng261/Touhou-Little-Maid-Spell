package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.event.maid.SlabClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端不预测「魂符收女仆」。
 *
 * <p>TLM 的 {@code SlabClickEvent.onInteract} 是 {@code InteractMaidEvent} 的监听器，
 * 而 {@code InteractMaidEvent} 在两边都会发：客户端在 {@code MultiPlayerGameMode.interact}
 * → {@code Player.interactOn} 里本地跑一遍 {@code mobInteract}，于是这个监听器也在客户端跑，
 * 里面是 {@code maid.discard()} —— 删的是<b>客户端实体</b>。
 *
 * <p>只要服务端因为任何理由拒绝这次交互（战败坐下的女仆就是这样：两边的拦截、
 * 锚定核心的移除保护……），两边就分叉成「客户端没有实体、服务端还有实体」，
 * 玩家看到的就是只掉渲染、女仆留在原地，而且重登前一直隐身。
 *
 * <p>所以客户端这一份预测整段砍掉：收起本来就必须由服务端拍板，服务端收走之后会自己发移除包
 * 和背包同步，客户端不需要抢跑。这么改之后，服务端无论拒绝还是同意，客户端都不会先动手。
 */
@Mixin(value = SlabClickEvent.class, remap = false)
public class SlabClickEventMixin {
    @Inject(method = "onInteract", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$skipClientStorePrediction(InteractMaidEvent event, CallbackInfo ci) {
        if (event.getMaid().level().isClientSide()) {
            ci.cancel();
        }
    }
}
