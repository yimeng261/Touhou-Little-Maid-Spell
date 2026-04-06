package com.github.yimeng261.maidspell.item.bauble.springBloomReturn;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class SpringBloomReturnBauble implements IMaidBauble {
    private static final String NBT_STACK_EXPIRIES = "spring_bloom_return_expiries";
    private static final String NBT_LAST_GAIN_TICK = "spring_bloom_return_last_gain_tick";
    private static final String NBT_TRIGGER_COOLDOWN_UNTIL = "spring_bloom_return_trigger_cooldown_until";
    private static final Map<UUID, Integer> EQUIPPED_SLOT_CACHE = new ConcurrentHashMap<>();

    public SpringBloomReturnBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        refreshEquippedSlotCache(maid, baubleItem);
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        EQUIPPED_SLOT_CACHE.remove(maid.getUUID());
    }

    public static void onSpellCast(EntityMaid maid, String modId, String spellId, LivingEntity target) {
        if (maid == null || maid.level().isClientSide) {
            return;
        }

        ItemStack stack = findEquippedStack(maid);
        if (stack.isEmpty()) {
            return;
        }

        long now = maid.level().getGameTime();
        pruneExpiredStacks(stack, now);
        CompoundTag tag = stack.getOrCreateTag();

        long lastGainTick = tag.getLong(NBT_LAST_GAIN_TICK);
        if (now - lastGainTick < Config.springBloomReturnGainCooldownTicks) {
            return;
        }

        List<Long> expiries = getExpiries(stack);
        if (expiries.size() >= Config.springBloomReturnMaxStacks) {
            return;
        }

        expiries.add(now + Config.springBloomReturnStackDurationTicks);
        expiries.sort(Long::compareTo);
        saveExpiries(stack, expiries);
        tag.putLong(NBT_LAST_GAIN_TICK, now);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!isHeavyDamage(target, event.getAmount())) {
            return;
        }

        if (target instanceof EntityMaid maid) {
            tryTrigger(event, maid, maid, findEquippedStack(maid));
            return;
        }

        if (target instanceof Player player) {
            EntityMaid protector = selectOwnerProtector(player);
            if (protector != null) {
                tryTrigger(event, protector, player, findEquippedStack(protector));
            }
        }
    }

    private static void tryTrigger(LivingDamageEvent event, EntityMaid maid, LivingEntity protectedTarget, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        long now = maid.level().getGameTime();
        pruneExpiredStacks(stack, now);
        List<Long> expiries = getExpiries(stack);
        if (expiries.isEmpty()) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        long triggerCooldownUntil = tag.getLong(NBT_TRIGGER_COOLDOWN_UNTIL);
        if (now < triggerCooldownUntil) {
            return;
        }

        expiries.sort(Long::compareTo);
        expiries.remove(0);
        saveExpiries(stack, expiries);
        tag.putLong(NBT_TRIGGER_COOLDOWN_UNTIL, now + Config.springBloomReturnTriggerCooldownTicks);

        float healEquivalent = (float) (protectedTarget.getMaxHealth() * Config.springBloomReturnHealRatio);
        event.setAmount(Math.max(0, event.getAmount() - healEquivalent));

        int favorabilityLevel = maid.getFavorabilityManager().getLevel();
        if (favorabilityLevel >= 2) {
            SpellBookManager.getOrCreateManager(maid).refundCooldowns(Config.springBloomReturnCooldownRefundRatio);
        }
        if (favorabilityLevel >= 3) {
            purgeOneNegativeEffect(protectedTarget);
        }
    }

    private static boolean isHeavyDamage(LivingEntity entity, float amount) {
        return amount >= Config.springBloomReturnDamageThreshold
            || amount >= entity.getMaxHealth() * Config.springBloomReturnDamageThresholdRatio;
    }

    private static EntityMaid selectOwnerProtector(Player player) {
        Map<UUID, EntityMaid> ownedMaids = Global.getOrCreatePlayerMaidMap(player.getUUID());
        return ownedMaids.values().stream()
            .filter(maid -> maid != null && maid.isAlive() && maid.level() == player.level())
            .filter(maid -> !findEquippedStack(maid).isEmpty())
            .max(Comparator.comparingInt(SpringBloomReturnBauble::getAvailableLayerCount))
            .orElse(null);
    }

    private static int getAvailableLayerCount(EntityMaid maid) {
        ItemStack stack = findEquippedStack(maid);
        if (stack.isEmpty()) {
            return 0;
        }
        pruneExpiredStacks(stack, maid.level().getGameTime());
        return getExpiries(stack).size();
    }

    private static ItemStack findEquippedStack(EntityMaid maid) {
        Integer cachedSlot = EQUIPPED_SLOT_CACHE.get(maid.getUUID());
        if (cachedSlot != null && cachedSlot >= 0 && cachedSlot < maid.getMaidBauble().getSlots()) {
            ItemStack cachedStack = maid.getMaidBauble().getStackInSlot(cachedSlot);
            if (cachedStack.is(MaidSpellItems.SPRING_BLOOM_RETURN.get())) {
                return cachedStack;
            }
        }

        for (int i = 0; i < maid.getMaidBauble().getSlots(); i++) {
            ItemStack stack = maid.getMaidBauble().getStackInSlot(i);
            if (stack.is(MaidSpellItems.SPRING_BLOOM_RETURN.get())) {
                EQUIPPED_SLOT_CACHE.put(maid.getUUID(), i);
                return stack;
            }
        }
        EQUIPPED_SLOT_CACHE.remove(maid.getUUID());
        return ItemStack.EMPTY;
    }

    private static void refreshEquippedSlotCache(EntityMaid maid, ItemStack baubleItem) {
        for (int i = 0; i < maid.getMaidBauble().getSlots(); i++) {
            ItemStack stack = maid.getMaidBauble().getStackInSlot(i);
            if (stack == baubleItem || stack.is(MaidSpellItems.SPRING_BLOOM_RETURN.get())) {
                EQUIPPED_SLOT_CACHE.put(maid.getUUID(), i);
                return;
            }
        }
        EQUIPPED_SLOT_CACHE.remove(maid.getUUID());
    }

    private static void purgeOneNegativeEffect(LivingEntity entity) {
        entity.getActiveEffects().stream()
            .filter(effect -> !effect.getEffect().isBeneficial())
            .max(Comparator.comparingInt(MobEffectInstance::getDuration))
            .map(MobEffectInstance::getEffect)
            .ifPresent(entity::removeEffect);
    }

    private static void pruneExpiredStacks(ItemStack stack, long now) {
        List<Long> expiries = getExpiries(stack);
        expiries.removeIf(expiry -> expiry <= now);
        saveExpiries(stack, expiries);
    }

    private static List<Long> getExpiries(ItemStack stack) {
        List<Long> expiries = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_STACK_EXPIRIES)) {
            return expiries;
        }
        for (long expiry : tag.getLongArray(NBT_STACK_EXPIRIES)) {
            expiries.add(expiry);
        }
        expiries.sort(Long::compareTo);
        return expiries;
    }

    private static void saveExpiries(ItemStack stack, List<Long> expiries) {
        stack.getOrCreateTag().putLongArray(
            NBT_STACK_EXPIRIES,
            expiries.stream().mapToLong(Long::longValue).toArray()
        );
    }
}
