package com.github.yimeng261.maidspell.item.bauble.hairpin;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.slf4j.Logger;

import java.util.List;

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

    public HairpinBauble() {
        NeoForge.EVENT_BUS.register(this);
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

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.tickCount % 10 == 0) {
            List<net.minecraft.core.Holder<MobEffect>> toRemove = maid.getActiveEffects().stream()
                    .map(MobEffectInstance::getEffect)
                    .filter(holder -> !holder.value().isBeneficial())
                    .toList();
            toRemove.forEach(maid::removeEffect);
        }
    }

    /**
     * 女仆 actuallyHurt 开头调用：盾牌、受击间隔与冰冻/头盔系数已结算，护甲尚未结算。
     * 这份伤害先经主人完整的受伤流程，女仆再承受主人实际掉的血量。
     */
    public static void redirectToOwner(EntityMaid maid, DamageSource source, DamageContainer container) {
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.HAIRPIN)) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof Player player)) {
            return;
        }
        if (source instanceof InfoDamageSource infoDamage && "hairpin_redirect".equals(infoDamage.msg_type)) {
            return;
        }

        RedirectContext existing = ACTIVE_REDIRECT.get();
        if (existing != null && existing.maid == maid) {
            return;
        }

        float amount = container.getNewDamage();
        if (amount <= 0.0f) {
            return;
        }

        RedirectContext context = new RedirectContext(maid, player);
        ACTIVE_REDIRECT.set(context);
        try {
            InfoDamageSource hairpinDamage = InfoDamageSource.create(player.level(), "hairpin_redirect", source);
            hairpinDamage.setSourceEntity(maid);
            player.setInvulnerable(false);
            player.invulnerableTime = 0;
            player.hurt(hairpinDamage, amount);
            container.setNewDamage(context.captured ? context.redirectedAmount : 0.0f);
        } finally {
            ACTIVE_REDIRECT.remove();
        }
    }

    @SubscribeEvent
    public void beforeMaidEffectAdded(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof EntityMaid maid && ItemsUtil.getBaubleSlotInMaid(maid, this) >= 0) {
            MobEffectInstance effectInstance = event.getEffectInstance();
            int fl = maid.getFavorabilityManager().getLevel();
            if (fl >= 3 && !effectInstance.getEffect().value().isBeneficial()) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
            if (fl >= 2 && effectInstance.getEffect().value().isBeneficial()) {
                int duration = Math.max((int) (effectInstance.getDuration() * Config.hairpinBeneficialEffectExtension),
                        effectInstance.getDuration() + Config.hairpinMinExtensionTicks);
                effectInstance.update(new MobEffectInstance(effectInstance.getEffect(), duration,
                        effectInstance.getAmplifier(), effectInstance.isAmbient(), effectInstance.isVisible()));
            }
        }
    }
}
