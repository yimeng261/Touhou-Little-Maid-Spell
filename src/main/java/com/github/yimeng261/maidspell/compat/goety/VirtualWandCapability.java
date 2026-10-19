package com.github.yimeng261.maidspell.compat.goety;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Exposes the proxy item handler used by Goety's IWand focus lookup. */
final class VirtualWandCapability implements ICapabilitySerializable<Tag> {
    private final ItemStack wandStack;
    private final LazyOptional<IItemHandler> holder;

    VirtualWandCapability(ItemStack wandStack) {
        this.wandStack = wandStack;
        this.holder = LazyOptional.of(() -> new VirtualSoulUsingItemHandler(this.wandStack));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                      @Nullable Direction side) {
        return ForgeCapabilities.ITEM_HANDLER.orEmpty(capability, this.holder);
    }

    @Override
    public Tag serializeNBT() {
        // The context is the authority; never write the bag focus into the
        // virtual item's share tag.
        return new CompoundTag();
    }

    @Override
    public void deserializeNBT(Tag nbt) {
        // No-op: capability contents are resolved from the live context.
    }
}
