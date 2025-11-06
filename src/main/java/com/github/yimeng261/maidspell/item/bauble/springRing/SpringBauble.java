package com.github.yimeng261.maidspell.item.bauble.springRing;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;

public class SpringBauble implements IMaidBauble {
    static {
        Global.baubleDamageCalcPre.put(MaidSpellItems.SPRING_RING.get(),(event, maid) -> {
            Float percent = 1 - maid.getHealth()/maid.getMaxHealth();
            if(percent > Config.springRingMaxDamageBonus){
                percent = (float)Config.springRingMaxDamageBonus;
            }
            event.setAmount(event.getAmount()*(1+percent));
            return null;
        });
    }
}
