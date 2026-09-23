package com.github.yimeng261.maidspell.compat.curios;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.event.DreamCrystalMaidEvents;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Registered only when Curios is installed. Player and maid passive effects share one implementation. */
public final class DreamCrystalPlayerEvents {
    private static final class EffectsHolder {
        private static final DreamCatCrystalBauble INSTANCE = new DreamCatCrystalBauble();
    }

    private static DreamCatCrystalBauble effects() {
        return EffectsHolder.INSTANCE;
    }
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Map<UUID, Map<UUID, Long>> ATTACKED_BOSSES = new HashMap<>();

    private DreamCrystalPlayerEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ItemStack crystal = DreamCrystalCurios.findCrystal(player);
        if (crystal.isEmpty()) {
            if (ACTIVE.remove(player.getUUID())) effects().removeWearerEffects(player);
            return;
        }
        if (ACTIVE.add(player.getUUID())) {
            for (var effect : List.copyOf(player.getActiveEffects())) {
                if (effect.getEffect().getCategory() != MobEffectCategory.BENEFICIAL) {
                    player.removeEffect(effect.getEffect());
                }
            }
            var advancement = player.server.getAdvancements().getAdvancement(
                new net.minecraft.resources.ResourceLocation("touhou_little_maid_spell", "dream_crystal/all_spells_mastered"));
            if (advancement != null) {
                for (String criterion : player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria()) {
                    player.getAdvancements().award(advancement, criterion);
                }
            }
        }
        effects().tickWearer(player, crystal);
        DreamCrystalPlayerCooldowns.clear(player);
        player.clearFire();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var item = player.getInventory().getItem(slot).getItem();
            if (player.getCooldowns().isOnCooldown(item)) player.getCooldowns().removeCooldown(item);
        }
        if (player.tickCount % 20 == 0) {
            long now = player.server.overworld().getGameTime();
            Map<UUID, Long> history = ATTACKED_BOSSES.get(player.getUUID());
            if (history != null) history.values().removeIf(time -> now - time >= 6000L);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48),
                mob -> mob.getTarget() == player)) {
                if (!mayRetaliate(mob, player)) mob.setTarget(null);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crystal = DreamCrystalCurios.findCrystal(player);
        if (crystal.isEmpty() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (TheRetreatDimension.isInRetreat(player) || DreamCrystalMaidEvents.isInvulnerable(crystal)
            || DreamCrystalMaidEvents.isImmuneTo(event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || DreamCrystalCurios.findCrystal(player).isEmpty()
            || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float amount = Math.min(event.getAmount() * 0.7F, 40.0F);
        if (DreamCrystalCurios.hasItem(player, MaidSpellItems.DOUBLE_HEART_CHAIN.get())) amount *= 0.5F;
        event.setAmount(amount);
        if (!TrueDamageUtil.isApplyingQueuedDamage()
            && DreamCrystalCurios.hasItem(player, MaidSpellItems.SLIVER_CERCIS.get())
            && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            TrueDamageUtil.dealTrueDamage(attacker,
                amount * (float) Config.silverCercisTrueDamageMultiplier * 2.0F, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hit(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
            || event.getAmount() <= 0 || DreamCrystalCurios.findCrystal(player).isEmpty()
            || TrueDamageUtil.isApplyingQueuedDamage()) return;
        LivingEntity target = event.getEntity();
        if (MaidSpellAllyResolver.areFriendly(player, target)) return;
        if (target.getType().is(Tags.EntityTypes.BOSSES)) {
            ATTACKED_BOSSES.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(target.getUUID(), player.server.overworld().getGameTime());
        }
        if (Config.dreamCrystalExtraTrueDamageEnabled) {
            TrueDamageUtil.dealTrueDamage(target, event.getAmount(), player);
        }
        if (Config.dreamCrystalSetNoAiEnabled && target instanceof Mob mob) {
            DreamCatCrystalBauble.freezeTarget(mob, player.server.overworld().getGameTime() + 20);
        }
        if (DreamCrystalCurios.hasItem(player, MaidSpellItems.CHAOS_BOOK.get())) {
            float damage = (float) Math.max(Config.chaosBookTrueDamageMin,
                target.getMaxHealth() * Config.chaosBookTrueDamagePercent) * 2.0F;
            TrueDamageUtil.dealTrueDamage(target, damage, player);
        }
        for (LivingEntity nearby : target.level().getEntitiesOfClass(LivingEntity.class,
            target.getBoundingBox().inflate(5), entity -> entity != target && entity.isAlive()
                && !(entity instanceof Player) && !(entity instanceof EntityMaid)
                && !MaidSpellAllyResolver.areFriendly(player, entity))) {
            TrueDamageUtil.dealTrueDamage(nearby, event.getAmount() * 0.1F, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void effect(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player
            && !DreamCrystalCurios.findCrystal(player).isEmpty()
            && event.getEffectInstance().getEffect().getCategory() != MobEffectCategory.BENEFICIAL) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ItemStack crystal = DreamCrystalCurios.findCrystal(player);
        if (!crystal.isEmpty() && effects().tryRevive(player, crystal)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void target(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player
            && !DreamCrystalCurios.findCrystal(player).isEmpty() && !mayRetaliate(event.getEntity(), player)) {
            event.setNewTarget(null);
        }
    }

    private static boolean mayRetaliate(LivingEntity mob, ServerPlayer player) {
        // An explicitly accepted duel must retain its challenger even before the first hit.
        if (net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).getPath()
            .equals("stellar_witch")) return true;
        Long time = ATTACKED_BOSSES.getOrDefault(player.getUUID(), Map.of()).get(mob.getUUID());
        return mob.getType().is(Tags.EntityTypes.BOSSES) && time != null
            && player.server.overworld().getGameTime() - time < 6000L;
    }

    @SubscribeEvent
    public static void eat(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().isEdible()
            && !DreamCrystalCurios.findCrystal(player).isEmpty()
            && DreamCrystalCurios.hasItem(player, MaidSpellItems.FRAGRANT_INGENUITY.get())) {
            effects().applyRandomBeneficialEffects(player, 2400, 4800);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (ACTIVE.remove(event.getEntity().getUUID())) effects().removeWearerEffects(event.getEntity());
        ATTACKED_BOSSES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        ATTACKED_BOSSES.clear();
    }
}
