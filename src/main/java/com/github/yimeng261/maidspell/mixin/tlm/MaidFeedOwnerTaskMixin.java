package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFeedOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity.FragrantIngenuityBauble;
import com.github.yimeng261.maidspell.utils.MaidSuppressionZone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidFeedOwnerTask.class, remap = false)
public class MaidFeedOwnerTaskMixin {
    /**
     * 压制区里的女仆不给主人喂食。
     *
     * <p>万法酒狐的驯服擂台要求玩家单挑，"不攻击"由
     * {@code MaidSpellAllyEvents} 拦住了，"不喂食"得堵在这儿——喂食是任务层的行为，
     * 不经过索敌也不产生伤害，两个事件谁都拦不到。
     */
    @Inject(
            method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void maidspell$suppressFeedInArena(ServerLevel worldIn,
                                               EntityMaid maid,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (MaidSuppressionZone.suppresses(maid)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V",
                    shift = At.Shift.AFTER
            )
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
