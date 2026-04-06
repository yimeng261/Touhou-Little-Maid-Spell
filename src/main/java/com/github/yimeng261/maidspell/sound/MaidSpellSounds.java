package com.github.yimeng261.maidspell.sound;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 女仆法术音效注册
 */
public class MaidSpellSounds {
    
    public static final DeferredRegister<SoundEvent> SOUNDS = 
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MaidSpellMod.MOD_ID);
    
    // 铃声音效
    @SuppressWarnings("removal")
    public static final RegistryObject<SoundEvent> WIND_SEEKING_BELL = SOUNDS.register("wind_seeking_bell",
        () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(MaidSpellMod.MOD_ID, "wind_seeking_bell"),16f));

}
