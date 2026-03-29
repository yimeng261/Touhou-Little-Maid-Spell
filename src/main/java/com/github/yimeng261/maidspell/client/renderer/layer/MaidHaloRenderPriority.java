package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.item.Item;

final class MaidHaloRenderPriority {
    private MaidHaloRenderPriority() {
    }

    static boolean shouldRenderDreamHalo(EntityMaid maid) {
        return resolve(maid) == HaloRenderType.DREAM;
    }

    static boolean shouldRenderTransmogHalo(EntityMaid maid) {
        return resolve(maid) == HaloRenderType.TRANSMOG;
    }

    static boolean shouldRenderUnholyHalo(EntityMaid maid) {
        return resolve(maid) == HaloRenderType.UNHOLY;
    }

    static boolean shouldRenderAscensionHalo(EntityMaid maid) {
        return resolve(maid) == HaloRenderType.ASCENSION;
    }

    private static HaloRenderType resolve(EntityMaid maid) {
        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) {
            return HaloRenderType.DREAM;
        }

        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.TRANSMOG_NECKLACE)) {
            return HaloRenderType.TRANSMOG;
        }

        Item unholyHat = MaidSpellItems.getUnholyHat();
        Item unholyHatHalo = MaidSpellItems.getUnholyHatHalo();
        if ((unholyHat != null && BaubleStateManager.hasBauble(maid, unholyHat))
                || (unholyHatHalo != null && BaubleStateManager.hasBauble(maid, unholyHatHalo))) {
            return HaloRenderType.UNHOLY;
        }

        Item ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo != null && BaubleStateManager.hasBauble(maid, ascensionHalo)) {
            return HaloRenderType.ASCENSION;
        }

        return HaloRenderType.NONE;
    }

    private enum HaloRenderType {
        NONE,
        DREAM,
        TRANSMOG,
        UNHOLY,
        ASCENSION
    }
}
