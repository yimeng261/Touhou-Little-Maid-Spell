package com.github.yimeng261.maidspell.item.bauble.transmogNecklace;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.item.ItemStack;

public class TransmogNecklaceBauble implements IMaidBauble {
    @Override
    public boolean syncClient(EntityMaid maid, ItemStack baubleItem) {
        return true;
    }

    public static ItemStack findTransmogNecklace(EntityMaid maid) {
        BaubleItemHandler handler = maid.getMaidBauble();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.is(MaidSpellItems.TRANSMOG_NECKLACE.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
