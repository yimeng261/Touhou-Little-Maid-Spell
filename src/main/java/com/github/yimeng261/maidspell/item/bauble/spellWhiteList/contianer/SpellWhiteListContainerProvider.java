package com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public class SpellWhiteListContainerProvider implements MenuProvider {
    private final ItemStackHandler scrollHandler;
    private final ItemStack spellWhiteListStack;
    private final int spellWhiteListSlot;
    
    public SpellWhiteListContainerProvider(ItemStackHandler scrollHandler, ItemStack spellWhiteListStack, int spellWhiteListSlot) {
        this.scrollHandler = scrollHandler;
        this.spellWhiteListStack = spellWhiteListStack;
        this.spellWhiteListSlot = spellWhiteListSlot;
    }
    
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.maidspell.blue_note");
    }
    
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SpellWhiteListContainer(id, playerInventory, scrollHandler, spellWhiteListStack, spellWhiteListSlot);
    }
} 