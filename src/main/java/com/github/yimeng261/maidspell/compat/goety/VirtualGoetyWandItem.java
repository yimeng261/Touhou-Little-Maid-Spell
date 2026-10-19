package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

/**
 * A non-obtainable IWand whose focus storage is proxied to the maid's FocusBag.
 */
public final class VirtualGoetyWandItem extends Item implements IWand {
    public VirtualGoetyWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * The virtual wand must not grant a school-specific staff bonus. The
     * selected focus provides the spell; it does not imply a staff school.
     */
    @Override
    public SpellType getSpellType() {
        return SpellType.NONE;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new VirtualWandCapability(stack);
    }

    /**
     * The virtual stack is never meant to be rendered or persisted with a
     * focus copy. Only the context id needs to travel with the stack.
     */
    @Override
    public @Nullable CompoundTag getShareTag(ItemStack stack) {
        return stack.getTag();
    }

    @Override
    public void readShareTag(ItemStack stack, @Nullable CompoundTag nbt) {
        stack.setTag(nbt);
    }
}
