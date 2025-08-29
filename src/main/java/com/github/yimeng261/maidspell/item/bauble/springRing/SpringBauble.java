package com.github.yimeng261.maidspell.item.bauble.springRing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;

public class SpringBauble implements IExtendBauble {
    @Override
    public void onRemove(EntityMaid maid) {

    }

    static {
        Global.bauble_damageProcessors_aft.put(MaidSpellItems.itemDesc(MaidSpellItems.SPRING_RING),(event, maid) -> {
            Float percent = 1 - maid.getHealth()/maid.getMaxHealth();
            if(percent > 0.5f){
                percent = 0.5f;
            }
            event.setAmount(event.getAmount()*(1+percent));
            return null;
        });
    }
}
