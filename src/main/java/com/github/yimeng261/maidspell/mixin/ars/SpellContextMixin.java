package com.github.yimeng261.maidspell.mixin.ars;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(value = com.hollingsworth.arsnouveau.api.spell.SpellContext.class, remap = false)
public abstract class SpellContextMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow @Nullable private LivingEntity caster;

    @Shadow public abstract Spell getSpell();

    @Shadow public abstract int getCurrentIndex();

    @Inject(method = "getUnwrappedCaster", at = @At("HEAD"), cancellable = true)
    private void maidspell$useOwnerForMaidSummonEffects(CallbackInfoReturnable<LivingEntity> cir) {
        if (this.caster instanceof EntityMaid maid && maid.getOwner() != null && maidspell$containsSummonEffect()) {
            LOGGER.debug("[MaidSpell/Ars] SpellContext uses maid owner as unwrapped caster maid={} owner={} index={} spell={}",
                    maid.getUUID(), maid.getOwner().getUUID(), getCurrentIndex(), maidspell$describeSpell());
            cir.setReturnValue(maid.getOwner());
        }
    }

    private boolean maidspell$containsSummonEffect() {
        Spell spell = getSpell();
        if (spell == null || spell.isEmpty()) {
            return false;
        }
        var parts = spell.unsafeList();
        for (int i = 0; i < parts.size(); i++) {
            String id = String.valueOf(parts.get(i).getRegistryName());
            if (id.equals("ars_nouveau:glyph_summon_wolves")
                    || id.equals("ars_nouveau:glyph_summon_decoy")
                    || id.equals("ars_nouveau:glyph_summon_steed")
                    || id.equals("ars_nouveau:glyph_summon_vex")
                    || id.equals("ars_nouveau:glyph_summon_undead")) {
                return true;
            }
        }
        return false;
    }

    private String maidspell$describeSpell() {
        Spell spell = getSpell();
        if (spell == null || spell.isEmpty()) {
            return "<null>";
        }
        var parts = spell.unsafeList();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(parts.get(i).getRegistryName());
        }
        return builder.toString();
    }
}
