package com.github.yimeng261.maidspell.item.bauble.chaosBook;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;

import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

import org.slf4j.Logger;

import java.util.IdentityHashMap;

/**
 * 混沌之书饰品实现
 * 将女仆造成的伤害转换为InfoDamageSources类型
 */
public class ChaosBookBauble implements IMaidBauble {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<IdentityHashMap<LivingEntity, Integer>> ACTIVE_CHAOS_DAMAGE =
        ThreadLocal.withInitial(IdentityHashMap::new);

    public ChaosBookBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void chaosBookProcess(EntityMaid maid, LivingEntity target) {
        if(BaubleStateManager.hasBauble(maid, MaidSpellItems.CHAOS_BOOK)) {
            float multiplier = BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL) ? 2.0f : 1.0f;
            float damage = (float) Math.max(Config.chaosBookTrueDamageMin,
                target.getMaxHealth() * Config.chaosBookTrueDamagePercent) * multiplier;
            TrueDamageUtil.dealTrueDamage(target, damage, maid);
        }
    }

    private static boolean isApplyingChaosDamage(LivingEntity target) {
        return target != null && ACTIVE_CHAOS_DAMAGE.get().containsKey(target);
    }

    private static void pushChaosDamage(LivingEntity target) {
        if (target == null) {
            return;
        }
        IdentityHashMap<LivingEntity, Integer> activeCalls = ACTIVE_CHAOS_DAMAGE.get();
        activeCalls.merge(target, 1, Integer::sum);
    }

    private static void popChaosDamage(LivingEntity target) {
        if (target == null) {
            return;
        }
        IdentityHashMap<LivingEntity, Integer> activeCalls = ACTIVE_CHAOS_DAMAGE.get();
        Integer depth = activeCalls.get(target);
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            activeCalls.remove(target);
            if (activeCalls.isEmpty()) {
                ACTIVE_CHAOS_DAMAGE.remove();
            }
        } else {
            activeCalls.put(target, depth - 1);
        }
    }

    static {
        Global.registerBaubleHurtHeadHandler(MaidSpellItems.CHAOS_BOOK.get(), context -> {
            EntityMaid maid = context.getSourceMaid();
            if (maid != null) {
                chaosBookProcess(maid, context.getTarget());
            }
        });

        // 注册女仆造成伤害时的处理器
        Global.baubleDamageHandlers.put(MaidSpellItems.CHAOS_BOOK.get(), (event, maid) -> {

            LivingEntity target = event.getEntity();
            DamageSource source = event.getSource();
            Float amount = event.getAmount();

            // Split damage can trigger third-party hurt callbacks that re-enter LivingDamageEvent.
            // Ignore nested processing for the same target to avoid recursive expansion.
            if (isApplyingChaosDamage(target)) {
                return null;
            }

            if(source instanceof InfoDamageSource infoDamage){
                if ("chaos_book".equals(infoDamage.msg_type)){
                    pushChaosDamage(target);
                    try {
                        InfoDamageSource newDamageSource = InfoDamageSource.create(target.level(), "chaos_book2", infoDamage.damage_source);
                        target.setInvulnerable(false);
                        target.invulnerableTime = 0;
                        target.hurt(newDamageSource, amount);
                        event.setCanceled(true);
                    } finally {
                        target.invulnerableTime = 0;
                        target.setInvulnerable(false);
                        popChaosDamage(target);
                    }
                }
            }else{
                pushChaosDamage(target);
                try {
                    int n = Config.chaosBookDamageSplitCount;
                    InfoDamageSource newDamageSource = InfoDamageSource.create(target.level(), "chaos_book", source);
                    float finalAmount = Math.max(amount/n, (float)Config.chaosBookMinSplitDamage);
                    for(int i=0;i<n;i++){
                        target.setInvulnerable(false);
                        target.invulnerableTime = 0;
                        target.hurt(newDamageSource, finalAmount);
                    }
                    event.setCanceled(true);
                } finally {
                    target.setInvulnerable(false);
                    target.invulnerableTime = 0;
                    popChaosDamage(target);
                }
            }
            return null;

        });
    }
}
