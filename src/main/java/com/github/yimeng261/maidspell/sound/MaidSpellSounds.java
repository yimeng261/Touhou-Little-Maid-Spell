package com.github.yimeng261.maidspell.sound;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 女仆法术音效注册
 */
public class MaidSpellSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, MaidSpellMod.MOD_ID);

    // 铃声音效
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_SEEKING_BELL = SOUNDS.register("wind_seeking_bell",
        () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(MaidSpellMod.MOD_ID, "wind_seeking_bell"),16f));

}
