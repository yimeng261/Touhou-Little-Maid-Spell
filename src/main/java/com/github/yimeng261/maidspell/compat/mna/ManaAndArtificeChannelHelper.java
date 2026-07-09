package com.github.yimeng261.maidspell.compat.mna;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidManaAndArtificeSpellData;
import net.minecraft.world.item.ItemStack;

/**
 * Bridge for M&A channeled spell entities spawned by maid casting.
 */
public final class ManaAndArtificeChannelHelper {
    private ManaAndArtificeChannelHelper() {
    }

    public static boolean isManagedMaidChannel(EntityMaid maid) {
        MaidManaAndArtificeSpellData data = MaidManaAndArtificeSpellData.get(maid.getUUID());
        return data != null && data.isManagingChannel();
    }

    public static ItemStack getManagedChannelItem(EntityMaid maid) {
        MaidManaAndArtificeSpellData data = MaidManaAndArtificeSpellData.get(maid.getUUID());
        if (data == null || !data.isManagingChannel()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = data.getCurrentSpellStack();
        if (stack == null || stack.isEmpty()) {
            stack = data.getCurrentSpellBook();
        }
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
