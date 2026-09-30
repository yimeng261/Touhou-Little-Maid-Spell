package com.github.yimeng261.maidspell.compat.curios;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.event.DreamCrystalMaidEvents;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.utils.DamageAbsorption;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 玩家佩戴梦云水晶的效果；仅在 curios 加载时注册，被动效果与女仆共用同一套实现。 */
public final class DreamCrystalPlayerEvents {
    private static final ResourceLocation ALL_SPELLS_MASTERED =
        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal/all_spells_mastered");
    private static final long BOSS_RETALIATION_TICKS = 6000L;

    private static final class EffectsHolder {
        private static final DreamCatCrystalBauble INSTANCE = new DreamCatCrystalBauble();
    }

    private static DreamCatCrystalBauble effects() {
        return EffectsHolder.INSTANCE;
    }

    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Map<UUID, Map<UUID, Long>> ATTACKED_BOSSES = new HashMap<>();

    private DreamCrystalPlayerEvents() {
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crystal = DreamCrystalCurios.findCrystal(player);
        if (crystal.isEmpty()) {
            if (ACTIVE.remove(player.getUUID())) effects().removeWearerEffects(player);
            return;
        }
        if (ACTIVE.add(player.getUUID())) {
            for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
                if (effect.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) {
                    player.removeEffect(effect.getEffect());
                }
            }
            AdvancementHolder advancement = player.server.getAdvancements().get(ALL_SPELLS_MASTERED);
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
            Item item = player.getInventory().getItem(slot).getItem();
            if (player.getCooldowns().isOnCooldown(item)) player.getCooldowns().removeCooldown(item);
        }
        if (player.tickCount % 20 == 0) {
            long now = player.server.overworld().getGameTime();
            Map<UUID, Long> history = ATTACKED_BOSSES.get(player.getUUID());
            if (history != null) history.values().removeIf(time -> now - time >= BOSS_RETALIATION_TICKS);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48),
                mob -> mob.getTarget() == player)) {
                if (!mayRetaliate(mob, player)) mob.setTarget(null);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crystal = DreamCrystalCurios.findCrystal(player);
        if (crystal.isEmpty() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (TheRetreatDimension.isInRetreat(player) || DreamCatCrystalBauble.isInvulnerable(crystal)
            || DreamCrystalMaidEvents.isImmuneTo(event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || DreamCrystalCurios.findCrystal(player).isEmpty()
            || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // 只裁剪扣除吸收后会写进血量的部分，吸收量原样加回由随后的吸收结算扣掉
        float absorbed = DamageAbsorption.absorbedPart(player, event.getNewDamage());
        float amount = Math.min((event.getNewDamage() - absorbed) * 0.7F, 40.0F);
        if (DreamCrystalCurios.hasItem(player, MaidSpellItems.DOUBLE_HEART_CHAIN.get())) amount *= 0.5F;
        event.setNewDamage(amount + absorbed);
        if (!TrueDamageUtil.isApplyingQueuedDamage()
            && DreamCrystalCurios.hasItem(player, MaidSpellItems.SLIVER_CERCIS.get())
            && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            TrueDamageUtil.dealTrueDamage(attacker,
                amount * (float) Config.silverCercisTrueDamageMultiplier * 2.0F, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hit(LivingDamageEvent.Pre event) {
        // Pre 只在通过无敌帧与格挡后触发；基数取护甲、附魔、药水减免前的伤害
        float baseDamage = preArmorDamage(event.getContainer());
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
            || baseDamage <= 0 || DreamCrystalCurios.findCrystal(player).isEmpty()
            || TrueDamageUtil.isApplyingQueuedDamage()) return;
        LivingEntity target = event.getEntity();
        if (MaidSpellAllyResolver.areFriendly(player, target)) return;
        if (target.getType().is(Tags.EntityTypes.BOSSES)) {
            ATTACKED_BOSSES.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(target.getUUID(), player.server.overworld().getGameTime());
        }
        if (Config.dreamCrystalExtraTrueDamageEnabled) {
            TrueDamageUtil.dealTrueDamage(target, baseDamage, player);
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
            TrueDamageUtil.dealTrueDamage(nearby, baseDamage * 0.1F, player);
        }
    }

    private static float preArmorDamage(DamageContainer container) {
        return container.getNewDamage()
            + container.getReduction(DamageContainer.Reduction.ARMOR)
            + container.getReduction(DamageContainer.Reduction.ENCHANTMENTS)
            + container.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void effect(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player
            && !DreamCrystalCurios.findCrystal(player).isEmpty()
            && event.getEffectInstance().getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ItemStack crystal = DreamCrystalCurios.findCrystal(player);
        if (!crystal.isEmpty() && DreamCatCrystalBauble.tryRevive(player, crystal)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void target(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
            && !DreamCrystalCurios.findCrystal(player).isEmpty() && !mayRetaliate(event.getEntity(), player)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    private static boolean mayRetaliate(LivingEntity mob, ServerPlayer player) {
        // 已接受的对决即使尚未出手也要保留挑战者
        if (BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("stellar_witch")) return true;
        Long time = ATTACKED_BOSSES.getOrDefault(player.getUUID(), Map.of()).get(mob.getUUID());
        return mob.getType().is(Tags.EntityTypes.BOSSES) && time != null
            && player.server.overworld().getGameTime() - time < BOSS_RETALIATION_TICKS;
    }

    @SubscribeEvent
    public static void eat(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().has(DataComponents.FOOD)
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
