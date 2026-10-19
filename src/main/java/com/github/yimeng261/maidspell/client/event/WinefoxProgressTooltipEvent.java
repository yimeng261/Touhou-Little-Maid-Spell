package com.github.yimeng261.maidspell.client.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.winefox.WinefoxProgressClientCache;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.winefox.WinefoxVowProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** 在归星的工具提示里显示星之魔女的认可进度，退出世界时清掉客户端缓存。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT)
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
