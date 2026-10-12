package com.github.yimeng261.maidspell.item.bauble.springRing;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;

public class SpringBauble implements IMaidBauble {
    static {
        Global.baubleDamagePreHandlers.put(MaidSpellItems.SPRING_RING.get(), (event, maid) -> {
            // 混沌之书拆出的子伤害来自已增伤的原伤害，不再重复加成
            if (event.getSource() instanceof InfoDamageSource infoDamage
                    && ("chaos_book".equals(infoDamage.msg_type) || "chaos_book2".equals(infoDamage.msg_type))) {
                return null;
            }
            Float percent = 1 - maid.getHealth()/maid.getMaxHealth();
            if(percent > Config.springRingMaxDamageBonus){
                percent = (float)Config.springRingMaxDamageBonus;
            }
            event.setNewDamage(event.getNewDamage() * (1 + percent));
            return null;
        });
    }
}
