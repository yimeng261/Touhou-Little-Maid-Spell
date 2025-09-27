package com.github.yimeng261.maidspell.item.bauble.bleedingHeart;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.player.Player;

public class BleedingHeartBauble implements IExtendBauble {
    @Override
    public void onRemove(EntityMaid maid) {}
    @Override
    public void onAdd(EntityMaid maid) {}

    static {
        Global.bauble_damageProcessors_aft.put(MaidSpellItems.itemDesc(MaidSpellItems.BLEEDING_HEART),(event, maid) -> {
            Float amount = event.getAmount();
            Player owner = (Player) maid.getOwner();
            if (owner != null) {
                owner.heal(amount*0.1f);
            }
            maid.heal(amount*0.1f);
            return null;
        });

    }

}
