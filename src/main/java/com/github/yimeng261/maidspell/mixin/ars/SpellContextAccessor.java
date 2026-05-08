package com.github.yimeng261.maidspell.mixin.ars;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

@Mixin(value = com.hollingsworth.arsnouveau.api.spell.SpellContext.class, remap = false)
public interface SpellContextAccessor {
    @Accessor("caster")
    @Nullable LivingEntity maidspell$getRawCaster();
}
