package com.github.yimeng261.maidspell.item.bauble.doubleHeartChain;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.player.Player;

public class DoubleHeartChainBauble implements IExtendBauble {
    static {
        // 女仆受到伤害时，平摊给主人
        Global.bauble_hurtProcessors_final.put(MaidSpellItems.itemDesc(MaidSpellItems.DOUBLE_HEART_CHAIN), (data) -> {
            EntityMaid maid = data.getMaid();
            Player owner = (Player) maid.getOwner();
            if (owner != null && !owner.level().isClientSide) {
                float originalDamage = data.getAmount();
                float sharedDamage = originalDamage * 0.5f; // 各承担50%伤害

                // 女仆承担50%伤害
                data.setAmount(sharedDamage);
                owner.hurt(new InfoDamageSource(), sharedDamage);
            }
            return null;
        });
    }
}