package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Plays the Winefox battle music while a nearby encounter is active. */
public final class WinefoxBossMusicController {
    private static final double LISTEN_RANGE = 96.0D;
    private static SoundInstance current;

    private WinefoxBossMusicController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        boolean activeEncounter = hasActiveEncounter(minecraft);
        if (current != null && (!minecraft.getSoundManager().isActive(current) || !activeEncounter)) {
            minecraft.getSoundManager().stop(current);
            current = null;
        }

        if (!activeEncounter || minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F
            || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F) {
            return;
        }

        if (current == null) {
            minecraft.getMusicManager().stopPlaying();
            current = new SimpleSoundInstance(MaidSpellSounds.WINEFOX_BGM.getId(), SoundSource.MUSIC,
                1.0F, 1.0F, SoundInstance.createUnseededRandom(), true, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            minecraft.getSoundManager().play(current);
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (current != null && sound != null && sound != current && sound.getSource() == SoundSource.MUSIC) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopCurrent(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        stopCurrent(Minecraft.getInstance());
    }

    private static boolean hasActiveEncounter(Minecraft minecraft) {
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.level == null) {
            return false;
        }
        double rangeSqr = LISTEN_RANGE * LISTEN_RANGE;
        return !minecraft.level.getEntitiesOfClass(MagicalWinefoxBossEntity.class,
                minecraft.player.getBoundingBox().inflate(LISTEN_RANGE),
                boss -> boss.isAlive() && boss.isBattleMusicActive()
                    && boss.distanceToSqr(minecraft.player) <= rangeSqr)
                .isEmpty();
    }

    private static void stopCurrent(Minecraft minecraft) {
        if (current != null) {
            minecraft.getSoundManager().stop(current);
            current = null;
        }
    }

}
