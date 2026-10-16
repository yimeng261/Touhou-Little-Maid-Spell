package com.github.yimeng261.maidspell.mixin.ftbquests;

import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 目标维度为共享归隐之地占位值的维度任务，在任意玩家的独立归隐之地里也算达成。 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.DimensionTask", remap = false)
public abstract class DimensionTaskMixin {
    @Shadow
    private ResourceKey<Level> dimension;

    @Inject(method = "canSubmit", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$acceptPrivateRetreat(@Coerce Object teamData,
                                              ServerPlayer player,
                                              CallbackInfoReturnable<Boolean> callback) {
        if (!player.isSpectator()
                && dimension != null
                && TheRetreatDimension.isSharedRetreatDimension(dimension.location())
                && TheRetreatDimension.matchesTaskDimension(
                        dimension.location(), player.level().dimension().location())) {
            callback.setReturnValue(true);
        }
    }
}
