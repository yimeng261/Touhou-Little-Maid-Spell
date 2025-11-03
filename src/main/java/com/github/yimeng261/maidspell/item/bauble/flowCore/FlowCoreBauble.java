package com.github.yimeng261.maidspell.item.bauble.flowCore;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.item.ItemStack;

public class FlowCoreBauble implements IExtendBauble {
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem){
        if(maid.tickCount % Config.flowCoreTickInterval == 0){
            maid.setHealth(maid.getHealth()+maid.getMaxHealth()*(float)Config.flowCoreHealthRegenRate*maid.getFavorabilityManager().getLevel());
        }
    }

    static {
        Global.bauble_hurtCalc_pre.put(MaidSpellItems.itemDesc(MaidSpellItems.FLOW_CORE),(data)->{
            EntityMaid maid = data.getMaid();
            data.setAmount(data.getAmount()*(1-(float)Config.flowCoreDamageReduction*maid.getFavorabilityManager().getLevel()));
            return null;
        });
    }
}
