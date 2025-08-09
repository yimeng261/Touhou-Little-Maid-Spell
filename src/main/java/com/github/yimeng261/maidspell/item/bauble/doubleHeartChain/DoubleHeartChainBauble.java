package com.github.yimeng261.maidspell.item.bauble.doubleHeartChain;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class DoubleHeartChainBauble implements IExtendBauble {
    @Override
    public void onRemove(EntityMaid maid) {}

    static {
        // 女仆受到伤害时，平摊给主人
        Global.bauble_hurtProcessors.put(MaidSpellItems.itemDesc(MaidSpellItems.DOUBLE_HEART_CHAIN), (event, maid) -> {
            Player owner = (Player) maid.getOwner();
            if (owner != null && !owner.level().isClientSide) {
                float originalDamage = event.getAmount();
                float sharedDamage = originalDamage * 0.5f; // 各承担50%伤害
                
                // 女仆承担50%伤害
                event.setAmount(sharedDamage);
                
                // 主人承担另外50%伤害
                owner.hurt(event.getSource(), sharedDamage);
            }
            return null;
        });
    }
} 