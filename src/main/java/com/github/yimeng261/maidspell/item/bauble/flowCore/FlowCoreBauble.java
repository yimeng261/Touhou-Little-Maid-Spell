package com.github.yimeng261.maidspell.item.bauble.flowCore;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

public class FlowCoreBauble implements IExtendBauble {
    @Override
    public void onRemove(EntityMaid maid) {}

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem){
        if(maid.tickCount % 10 == 0){
            maid.heal(maid.getMaxHealth()*0.025f*maid.getFavorabilityManager().getLevel());
        }
    }

    static {
        Global.bauble_hurtProcessors_pre.put(MaidSpellItems.itemDesc(MaidSpellItems.FLOW_CORE),(event,maid)->{
            event.setAmount(event.getAmount()*(1-0.2f*maid.getFavorabilityManager().getLevel()));
            return null;
        });
    }
}
