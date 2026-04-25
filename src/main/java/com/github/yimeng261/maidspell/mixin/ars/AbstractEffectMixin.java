package com.github.yimeng261.maidspell.mixin.ars;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.hollingsworth.arsnouveau.api.entity.ISummon;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep Ars summons owned by the maid even when summon effects receive the owner as their player context.
 */
@Mixin(value = com.hollingsworth.arsnouveau.api.spell.AbstractEffect.class, remap = false)
public abstract class AbstractEffectMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "summonLivingEntity", at = @At("HEAD"))
    private void maidspell$assignMaidSummonOwner(HitResult rayTraceResult, Level world, LivingEntity shooter, SpellStats augments,
                                                 SpellContext spellContext, SpellResolver resolver, ISummon summon, CallbackInfo ci) {
        LivingEntity originalCaster = spellContext != null ? ((SpellContextAccessor) spellContext).maidspell$getRawCaster() : null;
        if (originalCaster instanceof EntityMaid maid) {
            summon.setOwnerID(maid.getUUID());
            LivingEntity summonEntity = summon.getLivingEntity();
            LOGGER.debug("[MaidSpell/Ars] Assign Ars summon owner to maid maid={} shooter={} summon={}",
                    maid.getUUID(), shooter != null ? shooter.getUUID() : null,
                    summonEntity != null ? summonEntity.getType().builtInRegistryHolder().key().location() + "@" + summonEntity.getUUID() : "<null>");
        }
    }
}
