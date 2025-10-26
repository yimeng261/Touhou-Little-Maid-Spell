package com.github.yimeng261.maidspell.mixin;

import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;


@Mixin(CombatTracker.class)
public interface CombatTrackerAccessor {
    @Accessor("entries")
    List<CombatEntry> getEntries();
}
