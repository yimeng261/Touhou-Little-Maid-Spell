package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Live binding between one virtual wand stack and one FocusBag slot. */
final class VirtualWandContext {
    private final UUID id;
    private final EntityMaid maid;
    private final FocusBagItemHandler bagHandler;
    private final int focusSlot;
    private ItemStack focus;
    private boolean released;

    VirtualWandContext(UUID id, EntityMaid maid, FocusBagItemHandler bagHandler,
                       int focusSlot, ItemStack focus) {
        this.id = id;
        this.maid = maid;
        this.bagHandler = bagHandler;
        this.focusSlot = focusSlot;
        this.focus = focus == null ? ItemStack.EMPTY : focus;
    }

    UUID id() {
        return this.id;
    }

    UUID maidId() {
        return this.maid.getUUID();
    }

    ItemStack focus() {
        return this.released ? ItemStack.EMPTY : this.focus;
    }

    boolean isValid() {
        return !this.released
                && !this.maid.isRemoved()
                && this.maid.isAlive()
                && this.focusSlot >= 0
                && this.focusSlot < this.bagHandler.getSlots();
    }

    void setFocus(ItemStack stack) {
        if (this.released) {
            return;
        }
        this.focus = stack == null ? ItemStack.EMPTY : stack;
        this.bagHandler.setStackInSlot(this.focusSlot, this.focus);
    }

    ItemStack extractFocus(int amount, boolean simulate) {
        if (this.released || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = this.bagHandler.extractItem(this.focusSlot, amount, simulate);
        if (!simulate) {
            this.focus = this.bagHandler.getStackInSlot(this.focusSlot);
        }
        return extracted;
    }

    /** Persist in-place focus NBT mutations made by Goety during a callback. */
    void flush() {
        if (!this.released) {
            this.bagHandler.setStackInSlot(this.focusSlot, this.focus);
        }
    }

    void release() {
        this.released = true;
        this.focus = ItemStack.EMPTY;
    }
}
