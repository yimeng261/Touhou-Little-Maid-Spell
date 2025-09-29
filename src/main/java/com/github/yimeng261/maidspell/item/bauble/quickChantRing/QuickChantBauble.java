package com.github.yimeng261.maidspell.item.bauble.quickChantRing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;

public class QuickChantBauble implements IExtendBauble {
    static {
        Global.bauble_coolDownProcessors.put(MaidSpellItems.itemDesc(MaidSpellItems.QUICK_CHANT_RING),(coolDown)->{
            coolDown.cooldownticks = (int) (coolDown.cooldownticks*(1-0.25*coolDown.maid.getFavorabilityManager().getLevel()));
            return null;
        });
    }
}
