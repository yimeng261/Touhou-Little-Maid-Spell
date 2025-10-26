package com.github.yimeng261.maidspell.item.bauble.chaosBook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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
import net.minecraftforge.common.MinecraftForge;

import org.slf4j.Logger;

/**
 * 混沌之书饰品实现
 * 将女仆造成的伤害转换为InfoDamageSources类型
 */
public class ChaosBookBauble implements IExtendBauble {
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChaosBookBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void chaosBookProcess(EntityMaid maid, LivingEntity target) {
        if(BaubleStateManager.hasBauble(maid, MaidSpellItems.CHAOS_BOOK)) {
            float damage = (float) Math.max(5.0f,target.getMaxHealth()*0.01);
            TrueDamageUtil.dealTrueDamage(target, damage, maid);
        }
    }

    static {
        // 注册女仆造成伤害时的处理器
        Global.bauble_damageCalc_aft.put(MaidSpellItems.itemDesc(MaidSpellItems.CHAOS_BOOK), (event, maid) -> {

            LivingEntity target = event.getEntity();
            DamageSource source = event.getSource();
            Float amount = event.getAmount();
            int n = 5;

            if(source instanceof InfoDamageSource infoDamage){
                if ("chaos_book".equals(infoDamage.msg_type)){
                    // 使用安全的创建方法，避免网络同步问题
                    InfoDamageSource newDamageSource = InfoDamageSource.create(target.level(), "chaos_book2", infoDamage.damage_source);
                    target.setInvulnerable(false);
                    target.invulnerableTime = 0;
                    target.hurt(newDamageSource, amount);
                    event.setCanceled(true);
                }
                target.setInvulnerable(false);
                target.invulnerableTime = 0;
            }else{
                // 使用安全的创建方法，避免网络同步问题
                InfoDamageSource newDamageSource = InfoDamageSource.create(target.level(), "chaos_book", source);
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
