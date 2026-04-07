package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;

public final class MaidHaloRenderPriority {
    private MaidHaloRenderPriority() {
    }

    public static boolean shouldRenderDreamHalo(EntityMaid maid) {
        return resolve(maid) == HaloRenderType.DREAM;
    }

    public static boolean shouldRenderTransmogHalo(EntityMaid maid) {
        return resolve(maid) == HaloRenderType.TRANSMOG;
    }

    private static HaloRenderType resolve(EntityMaid maid) {
        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) {
            return HaloRenderType.DREAM;
        }
        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.TRANSMOG_NECKLACE)) {
            return HaloRenderType.TRANSMOG;
        }
        return HaloRenderType.NONE;
    }

    private enum HaloRenderType {
        NONE,
        DREAM,
        TRANSMOG
    }
}
