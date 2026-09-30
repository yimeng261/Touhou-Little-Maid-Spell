package com.github.yimeng261.maidspell.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.goety.GoetyMinionOwners;
import com.github.yimeng261.maidspell.compat.goety.GoetySpellEntityOwners;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellEntityOwners;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Central friendly-fire and target-alliance resolver for player/maid summon ecosystems.
 */
public final class MaidSpellAllyResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int OWNER_TRACE_LIMIT = 8;

    private static final String ARS_SUMMON = "com.hollingsworth.arsnouveau.api.entity.ISummon";
    private static final String SLASHBLADE_SHOOTABLE = "mods.flammpfeil.slashblade.entity.IShootable";
    private static final Map<String, Optional<Class<?>>> OPTIONAL_TYPES = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> OWNER_CAPABLE_CACHE = new ConcurrentHashMap<>();
    /** 每类实体在各桥接表里命中的表项，按表序；常见实体为空表 */
    private static final Map<Class<?>, List<BridgedGetter>> BRIDGED_GETTERS = new ConcurrentHashMap<>();

    private MaidSpellAllyResolver() {
    }

    public static boolean areFriendly(@Nullable Entity first, @Nullable Entity second) {
        if (first == null || second == null || first == second) {
            return first != null && first == second;
        }
        if (hasExplicitTeamAlliance(first, second)) {
            return true;
        }
        if (!couldHaveOwner(first) && !couldHaveOwner(second)) {
            return false;
        }
        return sharesAffinity(collectAffinityIds(first), second);
    }

    /**
     * 与 {@link #areFriendly} 判定相同，{@code caster} 一侧的主人链只算一次，供按同一个施法者过滤一批实体。
     */
    public static Predicate<Entity> friendlyTo(Entity caster) {
        Set<UUID> casterIds = collectAffinityIds(caster);
        boolean casterCouldHaveOwner = couldHaveOwner(caster);
        return target -> target == caster
                || hasExplicitTeamAlliance(caster, target)
                || ((casterCouldHaveOwner || couldHaveOwner(target)) && sharesAffinity(casterIds, target));
    }

    private static boolean sharesAffinity(Set<UUID> firstIds, Entity second) {
        for (UUID id : collectAffinityIds(second)) {
            if (firstIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitTeamAlliance(Entity first, Entity second) {
        Team firstTeam = first.getTeam();
        Team secondTeam = second.getTeam();
        if (firstTeam == null || secondTeam == null) {
            return false;
        }
        return first.isAlliedTo(second) || second.isAlliedTo(first);
    }

    public static boolean isFriendlyDamage(LivingEntity target, @Nullable Entity causing, @Nullable Entity direct) {
        // 玩家造成的伤害按目标原有规则处理（例如女仆对主人伤害的减免）
        if (causing instanceof Player || direct instanceof Player) {
            return false;
        }
        return areFriendly(target, causing)
                || areFriendly(target, direct)
                || resolveResponsibleEntity(direct).map(owner -> areFriendly(target, owner)).orElse(false);
    }

    /**
     * owner 链上（含自身）是否有一节是 {@code type}。
     * <p>与 {@link #resolveResponsibleEntity} 只取链尾不同，这里看整条链：女仆召唤物的链尾是玩家，但链上有女仆。
     * <p>深度由 {@link #OWNER_TRACE_LIMIT} 封顶，不分配集合；该方法挂在受击事件上，每次伤害都会调用。
     */
    public static boolean isOwnedBy(@Nullable Entity entity, Class<?> type) {
        Entity current = entity;
        for (int depth = 0; depth < OWNER_TRACE_LIMIT && current != null; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = getDirectOwner(current);
        }
        return false;
    }

    /**
     * 主人链上有女仆、且链上有一位在线玩家时，返回那位玩家；否则返回 {@code null}。
     *
     * <p>女仆本人、她的召唤物与弹体都算，链上先遇到玩家的那一节就算数。
     * 与 {@link #collectAffinityIds} 不同，这里只要玩家本人且必须在线。
     */
    @Nullable
    public static Player maidOwningPlayer(@Nullable Entity entity) {
        EntityMaid maid = null;
        Entity current = entity;
        for (int depth = 0; depth < OWNER_TRACE_LIMIT && current != null; depth++) {
            if (current instanceof EntityMaid found) {
                maid = found;
            } else if (current instanceof Player player) {
                return player;
            }
            current = getDirectOwner(current);
        }
        if (maid == null) {
            return null;
        }
        UUID ownerId = maid.getOwnerUUID();
        // 女仆的 getOwner() 走 PlayerList，测试假玩家等只在关卡玩家表里，按 UUID 在关卡里再找一次
        return ownerId == null ? null : maid.level().getPlayerByUUID(ownerId);
    }

    public static Optional<Entity> resolveResponsibleEntity(@Nullable Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Set<UUID> visited = new HashSet<>();
        Entity current = entity;
        for (int depth = 0; depth < OWNER_TRACE_LIMIT && current != null && visited.add(current.getUUID()); depth++) {
            Entity owner = getDirectOwner(current);
            if (owner == null) {
                return current == entity ? Optional.empty() : Optional.of(current);
            }
            current = owner;
        }
        return current == null || current == entity ? Optional.empty() : Optional.of(current);
    }

    public static Set<UUID> collectAffinityIds(@Nullable Entity entity) {
        Set<UUID> ids = new HashSet<>();
        collectAffinityIds(entity, ids, new HashSet<>(), 0);
        return ids;
    }

    private static void collectAffinityIds(@Nullable Entity entity, Set<UUID> ids, Set<UUID> visited, int depth) {
        if (entity == null || depth > OWNER_TRACE_LIMIT || !visited.add(entity.getUUID())) {
            return;
        }
        ids.add(entity.getUUID());

        Entity owner = getDirectOwner(entity);
        if (owner != null) {
            collectAffinityIds(owner, ids, visited, depth + 1);
        }

        UUID ownerId = getDirectOwnerId(entity);
        if (ownerId != null) {
            ids.add(ownerId);
            Entity resolved = findEntity(entity, ownerId);
            if (resolved != null) {
                collectAffinityIds(resolved, ids, visited, depth + 1);
            }
        }

        if (entity instanceof EntityMaid maid) {
            UUID maidOwnerId = maid.getOwnerUUID();
            if (maidOwnerId != null) {
                ids.add(maidOwnerId);
                collectAffinityIds(maid.getOwner(), ids, visited, depth + 1);
            }
        }
    }

    @Nullable
    private static Entity getDirectOwner(Entity entity) {
        // 按类缓存的判断先挡掉不可能有主人的实体，普通生物不用走下面整串 instanceof
        if (!couldHaveOwner(entity) || entity instanceof Player) {
            return null;
        }
        // 铁魔法召唤物、Goety 仆从和两边的法术实体按桥接表取，排在原版 OwnableEntity 之前
        Entity bridged = getBridgedOwner(entity);
        if (bridged != null) {
            return bridged;
        }
        if (isOptionalInstance(entity, ARS_SUMMON)) {
            Entity owner = invokeOptionalEntity(entity, ARS_SUMMON, "getOwnerAlt");
            if (owner != null) {
                return owner;
            }
            return findEntity(entity, invokeOptionalUuid(entity, ARS_SUMMON, "getOwnerUUID"));
        } else if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwner();
        } else if (isOptionalInstance(entity, SLASHBLADE_SHOOTABLE)) {
            return invokeOptionalEntity(entity, SLASHBLADE_SHOOTABLE, "getShooter");
        } else if (entity instanceof TraceableEntity traceable) {
            return traceable.getOwner();
        }
        return null;
    }

    /** 按表序取第一个非空的主人；某张表给出空值时交给下一张 */
    @Nullable
    private static Entity getBridgedOwner(Entity entity) {
        for (BridgedGetter bridged : bridgedGetters(entity.getClass())) {
            if (!bridged.bridge().enabled) {
                continue;
            }
            try {
                Entity owner = bridged.getter().ownerOf(entity);
                if (owner != null) {
                    return owner;
                }
            } catch (LinkageError e) {
                bridged.bridge().disable(e);
            }
        }
        return null;
    }

    private static List<BridgedGetter> bridgedGetters(Class<?> type) {
        return BRIDGED_GETTERS.computeIfAbsent(type, MaidSpellAllyResolver::findBridgedGetters);
    }

    private static List<BridgedGetter> findBridgedGetters(Class<?> type) {
        List<BridgedGetter> found = new ArrayList<>();
        for (OwnerBridge bridge : OwnerBridge.values()) {
            EntityOwnerGetter<?> getter = bridge.lookup(type);
            if (getter != null) {
                found.add(new BridgedGetter(bridge, getter));
            }
        }
        return found.isEmpty() ? List.of() : List.copyOf(found);
    }

    @Nullable
    private static UUID getGoetyOwnerId(Entity entity) {
        OwnerBridge bridge = OwnerBridge.GOETY_MINIONS;
        if (bridge.enabled) {
            try {
                return GoetyMinionOwners.getOwnerId(entity);
            } catch (LinkageError e) {
                bridge.disable(e);
            }
        }
        return null;
    }

    @Nullable
    private static UUID getDirectOwnerId(Entity entity) {
        if (entity instanceof EntityMaid maid) {
            return maid.getOwnerUUID();
        } else if (isOptionalInstance(entity, ARS_SUMMON)) {
            return invokeOptionalUuid(entity, ARS_SUMMON, "getOwnerUUID");
        }
        UUID goetyOwnerId = getGoetyOwnerId(entity);
        if (goetyOwnerId != null) {
            return goetyOwnerId;
        } else if (entity instanceof Player) {
            return null;
        } else if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID();
        } else if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            return owner != null ? owner.getUUID() : null;
        }
        return null;
    }

    private static boolean isOptionalInstance(Entity entity, String className) {
        return OPTIONAL_TYPES.computeIfAbsent(className, MaidSpellAllyResolver::loadOptionalType)
                .map(type -> type.isInstance(entity))
                .orElse(false);
    }

    private static Optional<Class<?>> loadOptionalType(String className) {
        try {
            return Optional.of(Class.forName(className, false, MaidSpellAllyResolver.class.getClassLoader()));
        } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    @Nullable
    private static Entity invokeOptionalEntity(Entity entity, String className, String methodName) {
        Object value = invokeOptionalNoArg(entity, className, methodName, Entity.class);
        return value instanceof Entity owner ? owner : null;
    }

    @Nullable
    private static UUID invokeOptionalUuid(Entity entity, String className, String methodName) {
        Object value = invokeOptionalNoArg(entity, className, methodName, UUID.class);
        return value instanceof UUID uuid ? uuid : null;
    }

    @Nullable
    private static Object invokeOptionalNoArg(Object target, String className, String methodName, Class<?> returnType) {
        // 调用方已用 isOptionalInstance 校验过 target 类型
        Optional<Method> cached = OPTIONAL_TYPES.computeIfAbsent(className, MaidSpellAllyResolver::loadOptionalType)
                .flatMap(type -> METHODS.computeIfAbsent(
                        new MethodKey(type, methodName, returnType),
                        MaidSpellAllyResolver::findPublicNoArgMethod));
        if (cached.isEmpty()) {
            return null;
        }
        try {
            return cached.get().invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Optional<Method> findPublicNoArgMethod(MethodKey key) {
        try {
            Method method = key.type().getMethod(key.name());
            if (key.returnType().isAssignableFrom(method.getReturnType())) {
                return Optional.of(method);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return Optional.empty();
    }

    @Nullable
    private static Entity findEntity(Entity context, @Nullable UUID id) {
        if (id == null || !(context.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(id);
        if (entity == null && context instanceof EntityMaid maid && maid.getOwner() != null && id.equals(maid.getOwnerUUID())) {
            return maid.getOwner();
        }
        if (entity == null) {
            Player player = serverLevel.getPlayerByUUID(id);
            if (player != null) {
                return player;
            }
        }
        return entity;
    }

    private static boolean couldHaveOwner(Entity entity) {
        return OWNER_CAPABLE_CACHE.computeIfAbsent(entity.getClass(), MaidSpellAllyResolver::checkOwnerCapable);
    }

    private static boolean checkOwnerCapable(Class<?> type) {
        if (EntityMaid.class.isAssignableFrom(type)
                || OwnableEntity.class.isAssignableFrom(type)
                || TraceableEntity.class.isAssignableFrom(type)
                || Player.class.isAssignableFrom(type)) {
            return true;
        }
        return !bridgedGetters(type).isEmpty()
                || isTypeAssignableTo(type, ARS_SUMMON)
                || isTypeAssignableTo(type, SLASHBLADE_SHOOTABLE);
    }

    private static boolean isTypeAssignableTo(Class<?> type, String className) {
        return OPTIONAL_TYPES.computeIfAbsent(className, MaidSpellAllyResolver::loadOptionalType)
                .map(optionalType -> optionalType.isAssignableFrom(type))
                .orElse(false);
    }

    private record MethodKey(Class<?> type, String name, Class<?> returnType) {
    }

    private record BridgedGetter(OwnerBridge bridge, EntityOwnerGetter<?> getter) {
    }

    /**
     * 可选模组的主人桥接表，各自独立失效：模组版本对不上时首次访问会抛 LinkageError，只停用出错的那一张。
     */
    private enum OwnerBridge {
        IRONS("irons_spellbooks", "铁魔法的召唤物和法术实体") {
            @Override
            EntityOwnerGetter<?> find(Class<?> type) {
                return IronsSpellEntityOwners.getterFor(type);
            }
        },
        GOETY_MINIONS("goety", "Goety 的仆从") {
            @Override
            EntityOwnerGetter<?> find(Class<?> type) {
                return GoetyMinionOwners.getterFor(type);
            }
        },
        GOETY_SPELLS("goety", "Goety 的法术实体") {
            @Override
            EntityOwnerGetter<?> find(Class<?> type) {
                return GoetySpellEntityOwners.getterFor(type);
            }
        };

        private final String description;
        private volatile boolean enabled;

        OwnerBridge(String modId, String description) {
            this.description = description;
            this.enabled = ModList.get().isLoaded(modId);
        }

        @Nullable
        abstract EntityOwnerGetter<?> find(Class<?> type);

        @Nullable
        EntityOwnerGetter<?> lookup(Class<?> type) {
            if (enabled) {
                try {
                    return find(type);
                } catch (LinkageError e) {
                    disable(e);
                }
            }
            return null;
        }

        void disable(LinkageError e) {
            if (enabled) {
                enabled = false;
                LOGGER.warn("[MaidSpell] 桥接类加载失败，友方判定不再识别{}", description, e);
            }
        }
    }
}
