package com.github.yimeng261.maidspell.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class TooltipHelper {
    private TooltipHelper() {
    }

    public static void addShiftTooltip(List<Component> tooltip, List<Component> flavorLines, List<Component> detailLines) {
        tooltip.addAll(flavorLines);
        if (detailLines.isEmpty()) {
            return;
        }

        if (Screen.hasShiftDown()) {
            tooltip.addAll(detailLines);
            return;
        }

        tooltip.add(Component.translatable(
                "tooltip.touhou_little_maid_spell.hold_shift",
                Component.keybind("key.keyboard.left.shift").withStyle(ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.DARK_GRAY));
    }
}
