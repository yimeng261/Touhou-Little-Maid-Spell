package com.github.yimeng261.maidspell.item.bauble.springRing;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;

public class SpringBauble implements IExtendBauble {
    static {
        Global.bauble_damageCalc_aft.put(MaidSpellItems.itemDesc(MaidSpellItems.SPRING_RING),(event, maid) -> {
            Float percent = 1 - maid.getHealth()/maid.getMaxHealth();
            if(percent > Config.springRingMaxDamageBonus){
                percent = (float)Config.springRingMaxDamageBonus;
            }
            event.setAmount(event.getAmount()*(1+percent));
            return null;
        });
    }
}
