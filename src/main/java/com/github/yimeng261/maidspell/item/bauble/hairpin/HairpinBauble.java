package com.github.yimeng261.maidspell.item.bauble.hairpin;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.slf4j.Logger;

/**
 * 发簪 Hairpin 饰品扩展行为
 */
public class HairpinBauble implements IExtendBauble {
    public static final Logger LOGGER = LogUtils.getLogger();

    public HairpinBauble() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if(maid.tickCount%10 == 0){
            maid.getActiveEffects().forEach(effect -> {
                if(!effect.getEffect().value().isBeneficial()){
                    effect.update(new MobEffectInstance(effect.getEffect(), 0, 0));
                }
            });
        }
    }

    static {
        // 注册女仆受伤时的处理器 - 将伤害转移给主人
        Global.bauble_commonHurtCalc_pre.put(MaidSpellItems.itemDesc(MaidSpellItems.HAIRPIN), (event, maid) -> {
            LivingEntity owner = maid.getOwner();
            DamageSource source = event.getSource();

            if (owner == null) {
                return null;
            }

            // 如果伤害源已经是InfoDamageSource且标记为hairpin_redirect，说明是回流伤害，不再转移
            if (source instanceof InfoDamageSource infoDamage && "hairpin_redirect".equals(infoDamage.msg_type)) {
                //LOGGER.debug("[hairpin] damage redirected finished, amount: {}, already redirected", event.getAmount());
                return null;
            }

            // 创建带有hairpin标记的InfoDamageSource转发给主人，使用安全的创建方法
            InfoDamageSource hairpinDamage = InfoDamageSource.create(owner.level(), "hairpin_redirect", source);
            hairpinDamage.setSourceEntity(maid);
            owner.setInvulnerable(false);
            owner.invulnerableTime = 0;
            owner.hurt(hairpinDamage, event.getAmount());
            event.setCanceled(true);

            //LOGGER.debug("[hairpin] damage redirected from maid {} to owner {}, amount: {}", maid.getUUID(), owner.getUUID(), event.getAmount());
            return null;
        });

        // 注册玩家受伤时的处理器 - 处理hairpin重定向的伤害
        Global.player_hurtCalc_aft.add((event, player) -> {
            DamageSource source = event.getSource();

            if (source instanceof InfoDamageSource infoDamage && "hairpin_redirect".equals(infoDamage.msg_type)) {
                EntityMaid maid = (EntityMaid) infoDamage.sourceEntity;
                maid.setInvulnerable(false);
                maid.invulnerableTime = 0;
                maid.hurt(source, event.getAmount());
                event.setCanceled(true);
                //LOGGER.debug("[hairpin] damage redirected back from owner {} to maid {}, amount: {}", player.getUUID(), maid.getUUID(), event.getAmount());
            }
            return null;
        });
    }

    @SubscribeEvent
    public void beforeMaidEffectAdded(MobEffectEvent.Applicable event) {
        if(event.getEntity() instanceof EntityMaid maid){
            //LOGGER.info("HairpinBauble beforeMaidEffectAdded");
            if(ItemsUtil.getBaubleSlotInMaid(maid,this)>=0){
                //LOGGER.info("HairpinBaubleEquipped");
                MobEffectInstance effectInstance = event.getEffectInstance();
                int fl = maid.getFavorabilityManager().getLevel();
                if(fl>=3){
                    if(!effectInstance.getEffect().value().isBeneficial()){
                        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                    }
                }
                if(fl>=2){
                    int duration = effectInstance.getDuration();
                    if(effectInstance.getEffect().value().isBeneficial()){
                        duration = Math.max((int)(duration*1.15), duration+15);
                        effectInstance.update(new MobEffectInstance(effectInstance.getEffect(), duration, event.getEffectInstance().getAmplifier(), event.getEffectInstance().isAmbient(), event.getEffectInstance().isVisible()));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void afterMaidEat(MaidAfterEatEvent event){
        EntityMaid maid = event.getMaid();
        if(ItemsUtil.getBaubleSlotInMaid(maid,this)>=0){
            maid.getFavorabilityManager().add(1);
        }
    }
}
