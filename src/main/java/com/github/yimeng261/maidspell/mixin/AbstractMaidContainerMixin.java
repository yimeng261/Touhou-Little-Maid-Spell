package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin用于移除女仆容器的距离检查限制
 * 允许玩家在任意距离打开女仆背包
 */
@Mixin(value = AbstractMaidContainer.class, remap = false)
public class AbstractMaidContainerMixin {

    @Final
    @Shadow
    protected EntityMaid maid;

    /**
     * 注入到 stillValid 方法开头，取消原方法并返回我们的逻辑
     */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = true)
    public void stillValid(Player playerIn, CallbackInfoReturnable<Boolean> cir) {
        if (maid == null) {
            return;
        }
        if (maid.getMaidBauble().containsItem(MaidSpellItems.ENDER_POCKET.get())) {
            boolean isValid = maid.isOwnedBy(playerIn) && !maid.isSleeping() && maid.isAlive();
            cir.setReturnValue(isValid);
        }
    }
}
