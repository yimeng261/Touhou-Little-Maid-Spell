package com.github.yimeng261.maidspell.sound;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

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

    // 复用 minecraft:intentionally_empty，让特定交互显式静音且不触发缺失音效警告。
    @SuppressWarnings("removal")
    public static final RegistryObject<SoundEvent> SILENT_MERCHANT_FEEDBACK = SOUNDS.register("silent_merchant_feedback",
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MaidSpellMod.MOD_ID, "silent_merchant_feedback")));

    @SuppressWarnings("removal")
    public static final RegistryObject<SoundEvent> STAR_SHADOW_STRIKE = SOUNDS.register("star_shadow_strike",
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MaidSpellMod.MOD_ID, "star_shadow_strike")));

    public static final RegistryObject<SoundEvent> WINEFOX_ATK_1 = registerWinefoxSound("atk1");
    public static final RegistryObject<SoundEvent> WINEFOX_ATK_2 = registerWinefoxSound("atk2");
    public static final RegistryObject<SoundEvent> WINEFOX_ATK_3 = registerWinefoxSound("atk3");
    public static final RegistryObject<SoundEvent> WINEFOX_ATK_3_READY = registerWinefoxSound("atk3ready");
    public static final RegistryObject<SoundEvent> WINEFOX_ATTACKED = registerWinefoxSound("atked");
    public static final RegistryObject<SoundEvent> WINEFOX_MAGIC = registerWinefoxSound("magic01");
    public static final RegistryObject<SoundEvent> WINEFOX_MAGIC_SHOOT = registerWinefoxSound("magic01_shoot");
    public static final RegistryObject<SoundEvent> WINEFOX_MAGIC_BOW = registerWinefoxSound("magicbow");
    public static final RegistryObject<SoundEvent> WINEFOX_VOICE = registerWinefoxSound("shengyin");
    public static final RegistryObject<SoundEvent> WINEFOX_BGM = SOUNDS.register("music.stellar_witch",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(MaidSpellMod.MOD_ID, "music.stellar_witch")));

    private static RegistryObject<SoundEvent> registerWinefoxSound(String name) {
        String id = "entity.stellar_witch." + name;
        return SOUNDS.register(id,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MaidSpellMod.MOD_ID, id)));
    }

    @Nullable
    public static SoundEvent getWinefoxSound(String name) {
        return switch (name) {
            case "atk1" -> WINEFOX_ATK_1.get();
            case "atk2" -> WINEFOX_ATK_2.get();
            case "atk3" -> WINEFOX_ATK_3.get();
            case "atk3ready" -> WINEFOX_ATK_3_READY.get();
            case "atked" -> WINEFOX_ATTACKED.get();
            case "magic01" -> WINEFOX_MAGIC.get();
            case "magic01_shoot" -> WINEFOX_MAGIC_SHOOT.get();
            case "magicbow" -> WINEFOX_MAGIC_BOW.get();
            case "shengyin" -> WINEFOX_VOICE.get();
            default -> null;
        };
    }
}
