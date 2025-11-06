package com.github.yimeng261.maidspell.item.bauble.quickChantRing;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;

public class QuickChantBauble implements IMaidBauble {
    static {
        Global.bauble_coolDownCalc.put(MaidSpellItems.QUICK_CHANT_RING.get(),(coolDown)->{
            coolDown.cooldownticks = (int) (coolDown.cooldownticks*(1-Config.quickChantRingCooldownReduction*coolDown.maid.getFavorabilityManager().getLevel()));
            return null;
        });
    }
}
