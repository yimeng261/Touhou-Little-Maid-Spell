package com.github.yimeng261.maidspell.item.bauble.blueNote.contianer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public class BlueNoteContainerProvider implements MenuProvider {
    private final ItemStackHandler scrollHandler;
    private final ItemStack blueNoteStack;
    private final int blueNoteSlot;

    public BlueNoteContainerProvider(ItemStackHandler scrollHandler, ItemStack blueNoteStack, int blueNoteSlot) {
        this.scrollHandler = scrollHandler;
        this.blueNoteStack = blueNoteStack;
        this.blueNoteSlot = blueNoteSlot;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.maidspell.blue_note");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new BlueNoteContainer(id, playerInventory, scrollHandler, blueNoteStack, blueNoteSlot);
    }
}