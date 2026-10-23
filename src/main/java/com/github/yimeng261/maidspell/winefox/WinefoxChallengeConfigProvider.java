package com.github.yimeng261.maidspell.winefox;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** Menu provider used when the dagger is shift-right-clicked. */
public final class WinefoxChallengeConfigProvider implements MenuProvider {
    private final int itemSlot;

    public WinefoxChallengeConfigProvider(int itemSlot) {
        this.itemSlot = itemSlot;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.touhou_little_maid_spell.winefox_challenge.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WinefoxChallengeConfigMenu(id, inventory, itemSlot);
    }
}
