package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A one-slot proxy over a FocusBag slot. Goety's IWand.getFocus casts the
 * item capability to SoulUsingItemHandler, so this must extend that exact
 * class rather than implement only IItemHandler.
 */
final class VirtualSoulUsingItemHandler extends SoulUsingItemHandler {
    private final ItemStack wandStack;

    VirtualSoulUsingItemHandler(ItemStack wandStack) {
        super(wandStack);
        this.wandStack = wandStack;
    }

    private VirtualWandContext context() {
        return VirtualWandManager.get(this.wandStack);
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }
        VirtualWandContext context = context();
        return context == null ? ItemStack.EMPTY : context.focus();
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot != 0 || stack.isEmpty() || !(stack.getItem() instanceof IFocus)) {
            return stack;
        }
        VirtualWandContext context = context();
        if (context == null || !context.focus().isEmpty()) {
            return stack;
        }
        if (simulate) {
            return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }
        context.setFocus(stack.copyWithCount(1));
        return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != 0 || amount <= 0) {
            return ItemStack.EMPTY;
        }
        VirtualWandContext context = context();
        return context == null ? ItemStack.EMPTY : context.extractFocus(amount, simulate);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (slot != 0) {
            return;
        }
        VirtualWandContext context = this.context();
        if (context != null) {
            context.setFocus(stack);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot == 0 && stack.getItem() instanceof IFocus;
    }

    @Override
    protected void onContentsChanged(int slot) {
        VirtualWandContext context = context();
        if (context != null) {
            context.flush();
        }
    }

    @Override
    public @NotNull CompoundTag serializeNBT() {
        return new CompoundTag();
    }

    @Override
    public void deserializeNBT(@NotNull CompoundTag nbt) {
        // No-op: the FocusBag is the authoritative storage.
    }
}
