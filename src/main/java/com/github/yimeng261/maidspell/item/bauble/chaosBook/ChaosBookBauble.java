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
import org.slf4j.Logger;

/**
 * 混沌之书饰品实现
 * 将女仆造成的伤害转换为InfoDamageSources类型
 */
public class ChaosBookBauble implements IMaidBauble {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void chaosBookProcess(EntityMaid maid, LivingEntity target) {
        if(BaubleStateManager.hasBauble(maid, MaidSpellItems.CHAOS_BOOK)) {
            float damage = (float) Math.max(Config.chaosBookTrueDamageMin, target.getMaxHealth() * Config.chaosBookTrueDamagePercent);
            TrueDamageUtil.dealTrueDamage(target, damage, maid);
        }
    }

    static {
        // 注册女仆造成伤害时的处理器
        Global.baubleDamageCalcPre.put(MaidSpellItems.CHAOS_BOOK.get(), (event, maid) -> {

            LivingEntity target = event.getEntity();
            DamageSource source = event.getSource();
            Float amount = event.getAmount();


            if(source instanceof InfoDamageSource infoDamage){
                if ("chaos_book".equals(infoDamage.msg_type)){
                    InfoDamageSource newDamageSource = InfoDamageSource.create(target.level(), "chaos_book2", infoDamage.damage_source);
                    target.setInvulnerable(false);
                    target.invulnerableTime = 0;
                    target.hurt(newDamageSource, amount);
                    event.setCanceled(true);
                    target.invulnerableTime = 0;
                    target.setInvulnerable(false);
                }
            }else{
                int n = Config.chaosBookDamageSplitCount;
                InfoDamageSource newDamageSource = InfoDamageSource.create(target.level(), "chaos_book", source);
                float finalAmount = Math.max(amount/n, (float)Config.chaosBookMinSplitDamage);
                for(int i=0;i<n;i++){
                    target.setInvulnerable(false);
                    target.invulnerableTime = 0;
                    target.hurt(newDamageSource, finalAmount);
                }
                target.setInvulnerable(false);
                target.invulnerableTime = 0;
                event.setCanceled(true);
            }
            return null;

        });
    }
}
