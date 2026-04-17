package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.entity.ai.behavior.BehaviorUtils.class)
public class BehaviorUtilsMixin {
    @Inject(
        method = "setWalkAndLookTargetMemories(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/behavior/PositionTracker;FI)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void maidspell$blockWalkTargetWritesWhileGuiOpen(
        LivingEntity livingEntity,
        PositionTracker positionTracker,
        float speedModifier,
        int closeEnoughDist,
        CallbackInfo ci
    ) {
        if (livingEntity instanceof EntityMaid maid && maid.guiOpening) {
            ci.cancel();
        }
    }
}
