package com.github.yimeng261.maidspell.mixin.ftbteams;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.ftbteams.FTBTeamsCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {
    @Inject(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void maidspell$checkFtbTeam(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof EntityMaid maid && FTBTeamsCompat.areFriendly(maid, entity)) {
            callback.setReturnValue(true);
        }
    }
}
