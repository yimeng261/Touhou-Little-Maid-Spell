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

    // 复用 minecraft:intentionally_empty，让特定交互显式静音且不触发缺失音效警告。
    public static final DeferredHolder<SoundEvent, SoundEvent> SILENT_MERCHANT_FEEDBACK = SOUNDS.register("silent_merchant_feedback",
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MaidSpellMod.MOD_ID, "silent_merchant_feedback")));

}
