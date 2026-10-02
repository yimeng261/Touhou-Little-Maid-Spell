package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxBossSleepGuard;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 星之魔女附近的床在上床流程最前面就判为不安全：不写重生点，白天也提示不安全。
 * NeoForge 的 CanPlayerSleepEvent 触发时重生点已经写入，所以不走事件。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerWinefoxSleepMixin {

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void maidspell$rejectBedNearWinefox(BlockPos pos,
                                                CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        if (WinefoxBossSleepGuard.isBossNearBed((ServerPlayer) (Object) this, pos)) {
            cir.setReturnValue(Either.left(Player.BedSleepingProblem.NOT_SAFE));
        }
    }
}
