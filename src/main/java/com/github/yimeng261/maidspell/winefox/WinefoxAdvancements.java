package com.github.yimeng261.maidspell.winefox;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

public final class WinefoxAdvancements {
    private static final ResourceLocation VOID_SECRET =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "star_journey/void_secret");

    private WinefoxAdvancements() {
    }

    public static void grantVoidSecret(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(VOID_SECRET);
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        if (progress.getOrStartProgress(advancement).isDone()) {
            return;
        }
        advancement.value().criteria().keySet().forEach(criterion ->
                progress.award(advancement, criterion));
    }
}
