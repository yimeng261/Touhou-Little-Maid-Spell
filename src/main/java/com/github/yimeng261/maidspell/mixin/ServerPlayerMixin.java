package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin用于阻止ServerPlayer在tick时自动关闭女仆容器
 * 防止女仆背包在远距离时被服务端自动关闭
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    /**
     * 重定向ServerPlayer.tick()中的stillValid检查
     * 对于女仆容器，跳过距离检查以防止自动关闭
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean redirectStillValidInTick(AbstractContainerMenu containerMenu, net.minecraft.world.entity.player.Player player) {
        // 如果是女仆容器，则总是返回true（跳过距离检查）
        if (containerMenu instanceof AbstractMaidContainer) {
            return true;
        }
        // 对于其他容器，保持原有的检查逻辑
        return containerMenu.stillValid(player);
    }
}
