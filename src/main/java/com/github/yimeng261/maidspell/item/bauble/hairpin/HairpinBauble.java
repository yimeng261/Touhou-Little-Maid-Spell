package com.github.yimeng261.maidspell.item.bauble.hairpin;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * 发簪 Hairpin 饰品扩展行为
 */
public class HairpinBauble implements IMaidBauble {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<RedirectContext> ACTIVE_REDIRECT = new ThreadLocal<>();

    private static final class RedirectContext {
        private final EntityMaid maid;
        private final Player owner;
        private float redirectedAmount;
        private boolean captured;

        private RedirectContext(EntityMaid maid, Player owner) {
            this.maid = maid;
            this.owner = owner;
        }
    }

    public static boolean captureRedirectedDamage(Player player, float amount) {
        RedirectContext context = ACTIVE_REDIRECT.get();
        if (context == null || context.owner != player) {
            return false;
        }
        context.redirectedAmount = Math.max(0.0f, amount);
        context.captured = true;
        return true;
    }

    public static boolean isSamplingOwnerDamage(LivingEntity entity) {
        RedirectContext context = ACTIVE_REDIRECT.get();
        return context != null && context.owner == entity;
    }

    public HairpinBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.tickCount % 10 == 0) {
            // 收集后再移除，避免迭代时 ConcurrentModification
            List<MobEffect> toRemove = maid.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(effect -> !effect.isBeneficial())
                .toList();
            toRemove.forEach(maid::removeEffect);
        }
    }

    static {
        // 注册女仆受伤时的处理器 - 将伤害转移给主人
        Global.baubleHurtEventHandlers.put(MaidSpellItems.HAIRPIN.get(), (event, maid) -> {
            LivingEntity owner = maid.getOwner();
            DamageSource source = event.getSource();

            if (!(owner instanceof Player player)) {
                return null;
            }

            // 如果伤害源已经是InfoDamageSource且标记为hairpin_redirect，说明是回流伤害，不再转移
            if (source instanceof InfoDamageSource infoDamage && "hairpin_redirect".equals(infoDamage.msg_type)) {
                return null;
            }

            RedirectContext existing = ACTIVE_REDIRECT.get();
            if (existing != null && existing.maid == maid) {
                return null;
            }

            RedirectContext context = new RedirectContext(maid, player);
            ACTIVE_REDIRECT.set(context);
            try {
                // Route the hit through the owner's full hurt/setHealth pipeline, then reuse the final HP loss on the maid.
                InfoDamageSource hairpinDamage = InfoDamageSource.create(player.level(), "hairpin_redirect", source);
                hairpinDamage.setSourceEntity(maid);
                player.setInvulnerable(false);
                player.invulnerableTime = 0;
                player.hurt(hairpinDamage, event.getAmount());
                event.setAmount(context.captured ? context.redirectedAmount : 0.0f);
            } finally {
                ACTIVE_REDIRECT.remove();
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
                    if(!effectInstance.getEffect().isBeneficial()){
                        event.setResult(Event.Result.DENY);
                    }
                }
                if(fl>=2){
                    int duration = effectInstance.getDuration();
                    if(effectInstance.getEffect().isBeneficial()){
                        duration = Math.max((int)(duration*Config.hairpinBeneficialEffectExtension), duration+Config.hairpinMinExtensionTicks);
                        effectInstance.update(new MobEffectInstance(effectInstance.getEffect(), duration, event.getEffectInstance().getAmplifier(), event.getEffectInstance().isAmbient(), event.getEffectInstance().isVisible()));
                    }
                }
            }
        }
    }

}
