package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketMaidProxyCache;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.MaidMovementHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

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
        if(maid == null){
            return;
        }
        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)) {
            boolean isValid = maid.isOwnedBy(playerIn) && !maid.isSleeping() && maid.isAlive();
            if (isValid && playerIn instanceof ServerPlayer serverPlayer
                    && maid.level() != serverPlayer.level()) {
                isValid = EnderPocketService.isRemoteSessionActive(serverPlayer, maid);
            }
            cir.setReturnValue(isValid);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), remap = true)
    private void maidspell$clearResidualMovementOnGuiClose(Player playerIn, CallbackInfo ci) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        MaidMovementHelper.stopAllMovement(maid);
    }

    /**
     * 重定向 Level.getEntity(int) 调用
     * 常规查找失败时，客户端使用独立代理，服务端使用已授权的远程会话。
     */
    @Redirect(
        method = "<init>",
        at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                remap = true
        ),
        remap = true
    )
    @Nullable
    private Entity redirectGetEntity(Level level, int requestedEntityId,
                                     @Nullable MenuType<?> menuType, int containerId,
                                     Inventory inventory, int entityId) {
        Entity entity = level.getEntity(requestedEntityId);
        if (entity instanceof EntityMaid localMaid) {
            if (!level.isClientSide() && inventory.player instanceof ServerPlayer serverPlayer) {
                EnderPocketService.syncRemoteProxyBeforeMenu(serverPlayer, localMaid);
            }
            return localMaid;
        }
        if (level.isClientSide()) {
            return EnderPocketMaidProxyCache.find(level, requestedEntityId);
        }
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            EntityMaid remoteMaid = EnderPocketService.resolveRemoteMaid(serverPlayer, requestedEntityId);
            if (remoteMaid != null) {
                EnderPocketService.syncRemoteProxyBeforeMenu(serverPlayer, remoteMaid);
            }
            return remoteMaid;
        }
        return null;
    }
}
