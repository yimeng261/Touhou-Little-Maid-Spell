package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.client.event.WinefoxProgressTooltipEvent;
import com.github.yimeng261.maidspell.client.winefox.WinefoxProgressClientCache;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 归星的工具提示按客户端缓存的誓约进度显示：进度未同步时不加行；已同步时第一行是灰色进度，
 * 两场常规胜利后追加紫色的下一步提示；其他物品不受影响。
 */
public final class WinefoxProgressClientScenes {
    private static final String PREFIX = "item.touhou_little_maid_spell.returning_star.";

    private WinefoxProgressClientScenes() {
    }

    public static List<Scene> integratedServer() {
        return List.of(Players.hostScene("client.winefoxProgress.tooltipFollowsCache", 10, WinefoxProgressClientScenes::tooltip));
    }

    private static List<String> tooltipKeys(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        ItemTooltipEvent event = new ItemTooltipEvent(stack, Minecraft.getInstance().player, lines,
                TooltipFlag.NORMAL, Item.TooltipContext.EMPTY);
        WinefoxProgressTooltipEvent.onItemTooltip(event);
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            keys.add(line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "<非翻译文本>");
        }
        return keys;
    }

    private static void tooltip(SceneContext ctx, ServerPlayer player) {
        ClientSide.call(ctx, () -> {
            boolean known = WinefoxProgressClientCache.isKnown();
            int wins = WinefoxProgressClientCache.getWins();
            boolean solo = WinefoxProgressClientCache.hasSoloWin();
            ItemStack star = new ItemStack(MaidSpellItems.RETURNING_STAR.get());
            List<String> result = new ArrayList<>();
            try {
                WinefoxProgressClientCache.clear();
                result.add("未同步 " + tooltipKeys(star));
                WinefoxProgressClientCache.update(1, false);
                result.add("1 场 " + tooltipKeys(star));
                WinefoxProgressClientCache.update(2, false);
                result.add("2 场 " + tooltipKeys(star));
                WinefoxProgressClientCache.update(2, true);
                result.add("2 场加专属胜利 " + tooltipKeys(star));
                result.add("其他物品 " + tooltipKeys(new ItemStack(net.minecraft.world.item.Items.STICK)));
            } finally {
                if (known) {
                    WinefoxProgressClientCache.update(wins, solo);
                } else {
                    WinefoxProgressClientCache.clear();
                }
            }
            return result;
        }, result -> ctx.check(result).as("归星工具提示的行").isEqualTo(List.of(
                "未同步 []",
                "1 场 [" + PREFIX + "progress]",
                "2 场 [" + PREFIX + "progress, " + PREFIX + "awaiting_dance]",
                "2 场加专属胜利 [" + PREFIX + "progress, " + PREFIX + "ready]",
                "其他物品 []")));
    }
}
