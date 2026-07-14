package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.RefreshMaidBrainMessage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RefreshMaidBrainMessage.class, remap = false)
public class RefreshMaidBrainMessageMixin {
    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$handleRemoteMaid(
            NetworkEvent.Context context, RefreshMaidBrainMessage message, CallbackInfo ci) {
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            ci.cancel();
            return;
        }
        Entity entity = sender.level().getEntity(message.entityId());
        if (!(entity instanceof EntityMaid)) {
            entity = EnderPocketService.resolveRemoteMaid(sender, message.entityId());
        }
        if (entity instanceof EntityMaid maid && maid.isOwnedBy(sender)
                && maid.level() instanceof ServerLevel maidLevel) {
            maid.refreshBrain(maidLevel);
        }
        ci.cancel();
    }
}
