package com.github.yimeng261.maidspell.item.bauble.springBloomReturn;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.utils.PortableTimerMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private static final int CLOCK_VERSION = 1;
    private static final int MAX_STORED_STACKS = 8;
    private static final int MIGRATION_GRACE_TICKS = 20;
    private static final String NBT_CLOCK_VERSION = "spring_bloom_return_clock_version";
    private static final String NBT_STACK_EXPIRIES = "spring_bloom_return_expiries";
    private static final String NBT_LAST_GAIN_TICK = "spring_bloom_return_last_gain_tick";
    private static final String NBT_GAIN_COOLDOWN_UNTIL = "spring_bloom_return_gain_cooldown_until";
    private static final String NBT_TRIGGER_COOLDOWN_UNTIL = "spring_bloom_return_trigger_cooldown_until";
    private static final Map<UUID, Integer> EQUIPPED_SLOT_CACHE = new ConcurrentHashMap<>();

    public SpringBloomReturnBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        refreshEquippedSlotCache(maid, baubleItem);
        if (!maid.level().isClientSide && !baubleItem.isEmpty()) {
            long now = getServerGameTime(maid);
            prepareTimerState(baubleItem, maid, now);
        }
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

        long now = getServerGameTime(maid);
        TimerState state = prepareTimerState(stack, maid, now);
        if (!state.writable()) {
            return;
        }

        if (state.gainCooldownUntil() != null && now < state.gainCooldownUntil()) {
            return;
        }

        int maxStacks = Math.min(MAX_STORED_STACKS, Math.max(0, Config.springBloomReturnMaxStacks));
        if (state.expiries().size() >= maxStacks) {
            return;
        }

        state.expiries().add(PortableTimerMath.saturatingAdd(now,
            Math.max(0L, Config.springBloomReturnStackDurationTicks)));
        state.expiries().sort(Long::compareTo);
        state.setGainCooldownUntil(deadlineOrNull(now, Config.springBloomReturnGainCooldownTicks));
        saveTimerState(stack, state, true);
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

        long now = getServerGameTime(maid);
        TimerState state = prepareTimerState(stack, maid, now);
        if (!state.writable()) {
            return;
        }

        if (state.expiries().isEmpty()) {
            return;
        }

        if (state.triggerCooldownUntil() != null && now < state.triggerCooldownUntil()) {
            return;
        }

        state.expiries().remove(0);
        state.setTriggerCooldownUntil(deadlineOrNull(now, Config.springBloomReturnTriggerCooldownTicks));
        saveTimerState(stack, state, true);

        float healEquivalent = (float) (protectedTarget.getMaxHealth() * Config.springBloomReturnHealRatio);
        event.setAmount(Math.max(0, event.getAmount() - healEquivalent));

        int favorabilityLevel = maid.getFavorabilityManager().getLevel();
        if (favorabilityLevel >= 2) {
            SpellBookManager.getOrCreateManager(maid).refundCooldowns(
                maid, Config.springBloomReturnCooldownRefundRatio);
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
        long now = getServerGameTime(maid);
        TimerState state = prepareTimerState(stack, maid, now);
        if (!state.writable()) {
            return 0;
        }
        return state.expiries().size();
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

    private static long getServerGameTime(EntityMaid maid) {
        return maid.level().getServer().overworld().getGameTime();
    }

    private static TimerState prepareTimerState(ItemStack stack, EntityMaid maid, long serverNow) {
        TimerState state = loadTimerState(stack, maid, serverNow);
        if (state.writable()) {
            pruneExpiredTimers(state, serverNow);
            saveTimerState(stack, state, state.hasManagedData());
        }
        return state;
    }

    private static TimerState loadTimerState(ItemStack stack, EntityMaid maid, long serverNow) {
        CompoundTag tag = stack.getTag();
        int storedVersion = tag != null && tag.contains(NBT_CLOCK_VERSION, Tag.TAG_ANY_NUMERIC)
            ? tag.getInt(NBT_CLOCK_VERSION)
            : 0;
        if (storedVersion > CLOCK_VERSION) {
            return TimerState.readOnly();
        }

        List<Long> expiries = new ArrayList<>();
        if (tag != null && tag.contains(NBT_STACK_EXPIRIES, Tag.TAG_LONG_ARRAY)) {
            for (long expiry : tag.getLongArray(NBT_STACK_EXPIRIES)) {
                expiries.add(expiry);
            }
        }

        Long gainCooldownUntil = getOptionalLong(tag, NBT_GAIN_COOLDOWN_UNTIL);
        Long triggerCooldownUntil = getOptionalLong(tag, NBT_TRIGGER_COOLDOWN_UNTIL);
        boolean legacy = storedVersion < CLOCK_VERSION;
        boolean hadManagedData = hasManagedTimerData(tag);
        long stackDuration = Math.max(0L, Config.springBloomReturnStackDurationTicks);
        if (legacy && hadManagedData) {
            long oldLocalNow = maid.level().getGameTime();
            expiries.replaceAll(expiry -> PortableTimerMath.migrateDeadline(
                expiry, oldLocalNow, serverNow, stackDuration, MIGRATION_GRACE_TICKS));

            Long lastGainTick = getOptionalLong(tag, NBT_LAST_GAIN_TICK);
            if (lastGainTick != null) {
                long gainCooldown = Math.max(0L, Config.springBloomReturnGainCooldownTicks);
                long migratedLastGain = PortableTimerMath.migrateTimestamp(
                    lastGainTick, oldLocalNow, serverNow,
                    gainCooldown, MIGRATION_GRACE_TICKS);
                gainCooldownUntil = PortableTimerMath.saturatingAdd(migratedLastGain, gainCooldown);
            } else if (gainCooldownUntil != null) {
                gainCooldownUntil = PortableTimerMath.migrateDeadline(
                    gainCooldownUntil, oldLocalNow, serverNow,
                    Config.springBloomReturnGainCooldownTicks, MIGRATION_GRACE_TICKS);
            }

            if (triggerCooldownUntil != null) {
                triggerCooldownUntil = PortableTimerMath.migrateDeadline(
                    triggerCooldownUntil, oldLocalNow, serverNow,
                    Config.springBloomReturnTriggerCooldownTicks, MIGRATION_GRACE_TICKS);
            }
        }

        expiries.replaceAll(expiry -> PortableTimerMath.migrateDeadline(
            expiry, serverNow, serverNow, stackDuration, 0L));
        if (gainCooldownUntil != null) {
            gainCooldownUntil = PortableTimerMath.migrateDeadline(
                gainCooldownUntil, serverNow, serverNow,
                Config.springBloomReturnGainCooldownTicks, 0L);
        }
        if (triggerCooldownUntil != null) {
            triggerCooldownUntil = PortableTimerMath.migrateDeadline(
                triggerCooldownUntil, serverNow, serverNow,
                Config.springBloomReturnTriggerCooldownTicks, 0L);
        }

        expiries.sort(Long::compareTo);
        int storedStackLimit = Math.min(MAX_STORED_STACKS,
            Math.max(0, Config.springBloomReturnMaxStacks));
        if (expiries.size() > storedStackLimit) {
            expiries = new ArrayList<>(expiries.subList(0, storedStackLimit));
        }
        return new TimerState(expiries, gainCooldownUntil, triggerCooldownUntil,
            true, legacy && hadManagedData);
    }

    private static void pruneExpiredTimers(TimerState state, long now) {
        state.expiries().removeIf(expiry -> expiry <= now);
        if (state.gainCooldownUntil() != null && state.gainCooldownUntil() <= now) {
            state.setGainCooldownUntil(null);
        }
        if (state.triggerCooldownUntil() != null && state.triggerCooldownUntil() <= now) {
            state.setTriggerCooldownUntil(null);
        }
    }

    private static void saveTimerState(ItemStack stack, TimerState state, boolean forceVersion) {
        if (!state.writable()) {
            return;
        }

        CompoundTag currentTag = stack.getTag();
        boolean needsWrite = state.requiresVersionWrite()
            || forceVersion && (currentTag == null || currentTag.getInt(NBT_CLOCK_VERSION) != CLOCK_VERSION)
            || currentTag != null && currentTag.contains(NBT_LAST_GAIN_TICK)
            || !longArrayEquals(currentTag, NBT_STACK_EXPIRIES, state.expiries())
            || !optionalLongEquals(currentTag, NBT_GAIN_COOLDOWN_UNTIL, state.gainCooldownUntil())
            || !optionalLongEquals(currentTag, NBT_TRIGGER_COOLDOWN_UNTIL, state.triggerCooldownUntil());
        if (!needsWrite) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (forceVersion || state.requiresVersionWrite()) {
            tag.putInt(NBT_CLOCK_VERSION, CLOCK_VERSION);
        }
        tag.remove(NBT_LAST_GAIN_TICK);
        putLongArrayOrRemove(tag, NBT_STACK_EXPIRIES, state.expiries());
        putOptionalLong(tag, NBT_GAIN_COOLDOWN_UNTIL, state.gainCooldownUntil());
        putOptionalLong(tag, NBT_TRIGGER_COOLDOWN_UNTIL, state.triggerCooldownUntil());
        state.markPersisted();
    }

    private static boolean hasManagedTimerData(CompoundTag tag) {
        return tag != null && (tag.contains(NBT_STACK_EXPIRIES)
            || tag.contains(NBT_LAST_GAIN_TICK)
            || tag.contains(NBT_GAIN_COOLDOWN_UNTIL)
            || tag.contains(NBT_TRIGGER_COOLDOWN_UNTIL)
            || tag.contains(NBT_CLOCK_VERSION));
    }

    private static Long getOptionalLong(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : null;
    }

    private static Long deadlineOrNull(long now, long durationTicks) {
        return durationTicks <= 0L ? null : PortableTimerMath.saturatingAdd(now, durationTicks);
    }

    private static boolean longArrayEquals(CompoundTag tag, String key, List<Long> values) {
        if (values.isEmpty()) {
            return tag == null || !tag.contains(key);
        }
        if (tag == null || !tag.contains(key, Tag.TAG_LONG_ARRAY)) {
            return false;
        }
        long[] stored = tag.getLongArray(key);
        if (stored.length != values.size()) {
            return false;
        }
        for (int i = 0; i < stored.length; i++) {
            if (stored[i] != values.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean optionalLongEquals(CompoundTag tag, String key, Long value) {
        if (value == null) {
            return tag == null || !tag.contains(key);
        }
        return tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC) && tag.getLong(key) == value;
    }

    private static void putLongArrayOrRemove(CompoundTag tag, String key, List<Long> values) {
        if (values.isEmpty()) {
            tag.remove(key);
        } else {
            tag.putLongArray(key, values.stream().mapToLong(Long::longValue).toArray());
        }
    }

    private static void putOptionalLong(CompoundTag tag, String key, Long value) {
        if (value == null) {
            tag.remove(key);
        } else {
            tag.putLong(key, value);
        }
    }

    private static final class TimerState {
        private final List<Long> expiries;
        private Long gainCooldownUntil;
        private Long triggerCooldownUntil;
        private final boolean writable;
        private boolean requiresVersionWrite;

        private TimerState(List<Long> expiries, Long gainCooldownUntil, Long triggerCooldownUntil,
                           boolean writable, boolean requiresVersionWrite) {
            this.expiries = expiries;
            this.gainCooldownUntil = gainCooldownUntil;
            this.triggerCooldownUntil = triggerCooldownUntil;
            this.writable = writable;
            this.requiresVersionWrite = requiresVersionWrite;
        }

        private static TimerState readOnly() {
            return new TimerState(new ArrayList<>(), null, null, false, false);
        }

        private List<Long> expiries() {
            return expiries;
        }

        private Long gainCooldownUntil() {
            return gainCooldownUntil;
        }

        private void setGainCooldownUntil(Long gainCooldownUntil) {
            this.gainCooldownUntil = gainCooldownUntil;
        }

        private Long triggerCooldownUntil() {
            return triggerCooldownUntil;
        }

        private void setTriggerCooldownUntil(Long triggerCooldownUntil) {
            this.triggerCooldownUntil = triggerCooldownUntil;
        }

        private boolean writable() {
            return writable;
        }

        private boolean requiresVersionWrite() {
            return requiresVersionWrite;
        }

        private boolean hasManagedData() {
            return requiresVersionWrite || !expiries.isEmpty()
                || gainCooldownUntil != null || triggerCooldownUntil != null;
        }

        private void markPersisted() {
            requiresVersionWrite = false;
        }
    }
}
