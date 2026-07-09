package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFeedOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity.FragrantIngenuityBauble;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidFeedOwnerTask.class, remap = false)
public class MaidFeedOwnerTaskMixin {
    @Inject(
            method = "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/OptionalInt;ifPresent(Ljava/util/function/IntConsumer;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void maidspell$applyOwnerBuffAfterFeed(ServerLevel worldIn,
                                                   EntityMaid maid,
                                                   long gameTimeIn,
                                                   CallbackInfo ci) {
        LivingEntity owner = maid.getOwner();
        if (owner instanceof Player player) {
            FragrantIngenuityBauble.applyOwnerFeedBuff(maid, player);
        }
    }
}
