package com.github.yimeng261.maidspell.item.bauble.doubleHeartChain;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;

import net.minecraft.world.entity.player.Player;

import java.util.IdentityHashMap;

public class DoubleHeartChainBauble implements IMaidBauble {
    private static final ThreadLocal<IdentityHashMap<EntityMaid, Integer>> ACTIVE_SHARED_DAMAGE =
        ThreadLocal.withInitial(IdentityHashMap::new);

    private static boolean isSharingDamage(EntityMaid maid) {
        return maid != null && ACTIVE_SHARED_DAMAGE.get().containsKey(maid);
    }

    private static void pushSharedDamage(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        ACTIVE_SHARED_DAMAGE.get().merge(maid, 1, Integer::sum);
    }

    private static void popSharedDamage(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        IdentityHashMap<EntityMaid, Integer> activeCalls = ACTIVE_SHARED_DAMAGE.get();
        Integer depth = activeCalls.get(maid);
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            activeCalls.remove(maid);
            if (activeCalls.isEmpty()) {
                ACTIVE_SHARED_DAMAGE.remove();
            }
        } else {
            activeCalls.put(maid, depth - 1);
        }
    }

    static {
        // 女仆受到伤害时，平摊给主人
        Global.baubleSetHealthFinalHandlers.put(MaidSpellItems.DOUBLE_HEART_CHAIN.get(), (data) -> {
            EntityMaid maid = data.getMaid();
            if (maid.getOwner() instanceof Player owner && !owner.level().isClientSide) {
                float originalDamage = data.getAmount();

                // 梦云水晶组合：主人不承担伤害，女仆只受 50%
                if (BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) {
                    data.setAmount(originalDamage * 0.5f);
                } else if (!isSharingDamage(maid)) {
                    float sharedDamage = originalDamage * (float)Config.doubleHeartChainShareRatio;
                    data.setAmount(sharedDamage);
                    pushSharedDamage(maid);
                    try {
                        var defaultDamageSource = owner.damageSources().generic();
                        var infoDamageSource = InfoDamageSource.create(owner.level(), "double_heart_chain", defaultDamageSource);
                        owner.hurt(infoDamageSource, sharedDamage);
                    } finally {
                        popSharedDamage(maid);
                    }
                }
            }
            return null;
        });
    }
} 
