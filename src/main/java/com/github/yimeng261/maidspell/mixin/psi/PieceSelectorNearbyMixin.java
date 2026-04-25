package com.github.yimeng261.maidspell.mixin.psi;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.providers.PsiProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.psi.api.spell.SpellContext;
import vazkii.psi.api.spell.wrapper.EntityListWrapper;
import vazkii.psi.common.spell.selector.entity.PieceSelectorNearby;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = PieceSelectorNearby.class, remap = false)
public abstract class PieceSelectorNearbyMixin {
    @Inject(method = "execute", at = @At("RETURN"), cancellable = true, remap = false)
    private void maidspell$removeFriendlyTargets(SpellContext context, CallbackInfoReturnable<Object> cir) {
        Object value = cir.getReturnValue();
        if (!(value instanceof EntityListWrapper entities)) {
            return;
        }

        Player caster = context.caster;
        UUID maidUuid = PsiProvider.getMaidUuidFromCaster(caster);
        if (maidUuid == null) {
            return;
        }

        boolean removed = false;
        List<Entity> filtered = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity instanceof Player || (entity instanceof EntityMaid maid && maid.getUUID().equals(maidUuid))) {
                removed = true;
                continue;
            }
            filtered.add(entity);
        }

        if (removed) {
            cir.setReturnValue(EntityListWrapper.make(filtered));
        }
    }
}
