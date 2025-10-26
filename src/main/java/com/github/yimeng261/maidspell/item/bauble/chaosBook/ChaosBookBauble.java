package com.github.yimeng261.maidspell.item.bauble.chaosBook;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * 混沌之书饰品实现
 * 将女仆造成的伤害转换为InfoDamageSources类型
 */
public class ChaosBookBauble implements IExtendBauble {
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChaosBookBauble() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMaidAttack(MaidAttackEvent event) {
        LOGGER.debug("[MaidSpell] ChaosBookBauble onMaidAttack");
        if(BaubleStateManager.hasBauble(event.getMaid(), MaidSpellItems.CHAOS_BOOK)) {
            LOGGER.debug("[MaidSpell] ChaosBookBauble onMaidAttack hasBauble");
            LivingEntity target = event.getEntity();
            float damage = (float) Math.max(5.0f,target.getMaxHealth()*0.01);
            TrueDamageUtil.dealTrueDamage(target, damage);
            WoundRimeBladeBauble.updateWoundRimeMap(event.getMaid(), target, damage);
        }
    }

    static {
        // 注册女仆造成伤害时的处理器
        Global.bauble_damageProcessors_pre.put(MaidSpellItems.itemDesc(MaidSpellItems.CHAOS_BOOK), (event, maid) -> {

            LivingEntity target = event.getEntity();
            DamageSource source = event.getSource();
            Float amount = event.getAmount();
            int n = 5;

            if(source instanceof InfoDamageSource infoDamage){
                if ("chaos_book".equals(infoDamage.msg_type)){
                    InfoDamageSource newDamageSource = new InfoDamageSource("chaos_book2", infoDamage.damage_source);
                    target.setInvulnerable(false);
                    target.invulnerableTime = 0;
                    target.hurt(newDamageSource, amount);
                    event.setCanceled(true);
                }
                target.setInvulnerable(false);
                target.invulnerableTime = 0;
            }else{
                InfoDamageSource newDamageSource = new InfoDamageSource("chaos_book", source);
                float finalAmount = Math.max(amount/n, 1.0f);
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
