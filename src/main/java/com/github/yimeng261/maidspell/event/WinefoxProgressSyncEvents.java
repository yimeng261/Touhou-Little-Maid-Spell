package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.WinefoxProgressMessage;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WinefoxProgressSyncEvents {
    private WinefoxProgressSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WinefoxChallengeProgress.copy(event.getOriginal(), player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new WinefoxProgressMessage(
                WinefoxChallengeProgress.getWins(player),
                WinefoxChallengeProgress.hasSoloWin(player))
        );
    }
}
