package com.github.yimeng261.maidspell.client.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.winefox.WinefoxProgressClientCache;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.winefox.WinefoxVowProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WinefoxProgressTooltipEvent {
    private WinefoxProgressTooltipEvent() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getEntity() == null
            || !event.getItemStack().is(MaidSpellItems.RETURNING_STAR.get())
            || !WinefoxProgressClientCache.isKnown()) {
            return;
        }
        int wins = WinefoxProgressClientCache.getWins();
        boolean soloWin = WinefoxProgressClientCache.hasSoloWin();
        event.getToolTip().add(WinefoxVowProgress.progress(wins).withStyle(ChatFormatting.GRAY));
        MutableComponent nextStep = WinefoxVowProgress.nextStep(wins, soloWin);
        if (nextStep != null) {
            event.getToolTip().add(nextStep.withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WinefoxProgressClientCache.clear();
    }
}
