package com.github.yimeng261.maidspell.item.bauble.bleedingHeart;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.player.Player;

public class BleedingHeartBauble implements IExtendBauble {
    static {
        Global.bauble_damageCalc_aft.put(MaidSpellItems.itemDesc(MaidSpellItems.BLEEDING_HEART),(event, maid) -> {
            Float amount = event.getAmount();
            Player owner = (Player) maid.getOwner();
            if (owner != null) {
                owner.heal(amount*(float)Config.bleedingHeartHealRatio);
            }
            maid.heal(amount*(float)Config.bleedingHeartHealRatio);
            return null;
        });

    }

}
