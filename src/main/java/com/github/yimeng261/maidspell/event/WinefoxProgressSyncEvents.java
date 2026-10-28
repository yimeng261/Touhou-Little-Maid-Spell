package com.github.yimeng261.maidspell.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.message.WinefoxProgressMessage;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 登录与重生时把玩家的誓约进度同步给客户端。进度按 UUID 存档，重生不需要复制。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
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
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new WinefoxProgressMessage(
            WinefoxChallengeProgress.getWins(player.server, player.getUUID()),
            WinefoxChallengeProgress.hasSoloWin(player.server, player.getUUID())));
    }
}
