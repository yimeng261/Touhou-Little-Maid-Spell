package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 仅在运行时生效的友伤保护，处理绕过常规伤害事件的友方伤害。 */
public final class FriendlyDamageHealthGuard {
    private static final long WINDOW_TICKS = 1L;
    private static final Map<UUID, GuardEntry> ACTIVE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Deque<DamageFrame>> DAMAGE_FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> RESTORE_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private FriendlyDamageHealthGuard() {
    }

    public static void onDamageAttempt(LivingEntity target, DamageSource source) {
        if (!Config.powerfulFriendlyFireProtection) {
            if (!ACTIVE.isEmpty()) {
                release(target);
            }
            return;
        }
        if (target.level().isClientSide() || source == null) {
            return;
        }

        boolean friendly = MaidSpellAllyResolver.isFriendlyDamage(
                target, source.getEntity(), source.getDirectEntity());
        if (!friendly) {
            release(target);
            return;
        }

        Deque<DamageFrame> frames = DAMAGE_FRAMES.get();
        boolean alreadyCaptured = frames.stream()
                .anyMatch(frame -> frame.matches(target, source));
        if (!alreadyCaptured) {
            frames.push(new DamageFrame(target.getUUID(), source));
        }
        record(target, source, alreadyCaptured);
    }

    public static void leaveDamageAttempt(LivingEntity target, DamageSource source) {
        Deque<DamageFrame> frames = DAMAGE_FRAMES.get();
        Iterator<DamageFrame> iterator = frames.iterator();
        while (iterator.hasNext()) {
            DamageFrame frame = iterator.next();
            if (frame.matches(target, source)) {
                iterator.remove();
                break;
            }
        }
        if (frames.isEmpty()) {
            DAMAGE_FRAMES.remove();
        }
    }

    public static void restoreIfNeeded(LivingEntity target) {
        if (!Config.powerfulFriendlyFireProtection) {
            release(target);
            return;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (target.level().isClientSide() || target.isRemoved()) {
            return;
        }

        GuardEntry entry = activeEntry(target.getUUID(), target.level().getGameTime());
        if (entry == null) {
            return;
        }

        float health = target.getHealth();
        float absorption = target.getAbsorptionAmount();
        if (health >= entry.healthFloor && absorption >= entry.absorptionFloor) {
            return;
        }
        if (RESTORE_DEPTH.get() > 0) {
            return;
        }

        RESTORE_DEPTH.set(RESTORE_DEPTH.get() + 1);
        try {
            float restoredHealth = Math.min(entry.healthFloor, target.getMaxHealth());
            target.setHealth(Math.max(health, restoredHealth));
            target.setAbsorptionAmount(Math.max(absorption, entry.absorptionFloor));
            entry.restoreCount++;
        } finally {
            int depth = RESTORE_DEPTH.get() - 1;
            if (depth == 0) {
                RESTORE_DEPTH.remove();
            } else {
                RESTORE_DEPTH.set(depth);
            }
        }
    }

    public static boolean shouldBlockDeath(LivingEntity target, DamageSource source) {
        if (!Config.powerfulFriendlyFireProtection
                || ACTIVE.isEmpty()
                || target.level().isClientSide() || source == null) {
            return false;
        }
        GuardEntry entry = activeEntry(target.getUUID(), target.level().getGameTime());
        if (entry == null) {
            return false;
        }
        boolean friendly = MaidSpellAllyResolver.isFriendlyDamage(
                target, source.getEntity(), source.getDirectEntity());
        if (friendly) {
            entry.blockedDeathCount++;
        }
        return friendly;
    }

    public static boolean shouldBlockRemoval(Entity entity, Entity.RemovalReason reason) {
        if (!Config.powerfulFriendlyFireProtection
                || ACTIVE.isEmpty()
                || reason != Entity.RemovalReason.KILLED
                || !(entity instanceof LivingEntity target)
                || target.level().isClientSide()) {
            return false;
        }

        long now = target.level().getGameTime();
        GuardEntry entry = activeEntry(target.getUUID(), now);
        if (entry == null) {
            return false;
        }
        restoreIfNeeded(target);
        entry.blockedRemovalCount++;
        return entry.healthFloor > 0.0F && target.getHealth() >= entry.healthFloor;
    }

    public static void onServerTickEnd(MinecraftServer server) {
        if (!Config.powerfulFriendlyFireProtection) {
            ACTIVE.clear();
            DAMAGE_FRAMES.remove();
            RESTORE_DEPTH.remove();
            return;
        }
        if (ACTIVE.isEmpty()) {
            DAMAGE_FRAMES.remove();
            RESTORE_DEPTH.remove();
            return;
        }
        for (Map.Entry<UUID, GuardEntry> mapEntry : new ArrayList<>(ACTIVE.entrySet())) {
            GuardEntry entry = mapEntry.getValue();
            ServerLevel level = server.getLevel(entry.dimension);
            Entity entity = level == null ? null : level.getEntity(entry.targetId);
            // 条目按目标所在维度的时钟盖戳，过期也要按同一个时钟判断，归隐之地的时钟与主世界独立
            long now = level == null ? server.overworld().getGameTime() : level.getGameTime();

            if (entity instanceof LivingEntity target) {
                restoreIfNeeded(target);
            }
            if (entity == null || entity.isRemoved() || now > entry.expiresAtGameTime) {
                ACTIVE.remove(mapEntry.getKey(), entry);
            }
        }

        DAMAGE_FRAMES.remove();
        RESTORE_DEPTH.remove();
    }

    private static void record(LivingEntity target, DamageSource source, boolean duplicate) {
        long now = target.level().getGameTime();
        GuardEntry entry = ACTIVE.computeIfAbsent(target.getUUID(),
                id -> new GuardEntry(id, target.level().dimension()));
        entry.dimension = target.level().dimension();
        entry.healthFloor = Math.max(entry.healthFloor, target.getHealth());
        entry.absorptionFloor = Math.max(entry.absorptionFloor, target.getAbsorptionAmount());
        entry.expiresAtGameTime = Math.max(entry.expiresAtGameTime, now + WINDOW_TICKS);
        entry.causingEntityId = entityId(source.getEntity());
        entry.directEntityId = entityId(source.getDirectEntity());
        if (!duplicate) {
            entry.hitCount++;
        }
    }

    private static GuardEntry activeEntry(UUID targetId, long now) {
        GuardEntry entry = ACTIVE.get(targetId);
        if (entry == null) {
            return null;
        }
        if (now > entry.expiresAtGameTime) {
            ACTIVE.remove(targetId, entry);
            return null;
        }
        return entry;
    }

    private static void release(LivingEntity target) {
        ACTIVE.remove(target.getUUID());
    }

    private static UUID entityId(Entity entity) {
        return entity == null ? null : entity.getUUID();
    }

    private record DamageFrame(UUID targetId, DamageSource source) {
        private boolean matches(LivingEntity target, DamageSource candidate) {
            return targetId.equals(target.getUUID()) && source == candidate;
        }
    }

    private static final class GuardEntry {
        private final UUID targetId;
        private ResourceKey<Level> dimension;
        private float healthFloor;
        private float absorptionFloor;
        private long expiresAtGameTime;
        private UUID causingEntityId;
        private UUID directEntityId;
        private int hitCount;
        private int restoreCount;
        private int blockedDeathCount;
        private int blockedRemovalCount;

        private GuardEntry(UUID targetId, ResourceKey<Level> dimension) {
            this.targetId = targetId;
            this.dimension = dimension;
        }
    }
}
