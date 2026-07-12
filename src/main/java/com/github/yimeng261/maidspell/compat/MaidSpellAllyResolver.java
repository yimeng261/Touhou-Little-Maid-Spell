package com.github.yimeng261.maidspell.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.scores.Team;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central friendly-fire and target-alliance resolver for player/maid summon ecosystems.
 */
public final class MaidSpellAllyResolver {
    private static final int OWNER_TRACE_LIMIT = 8;

    private static final String IRONS_MAGIC_SUMMON = "io.redspace.ironsspellbooks.entity.mobs.IMagicSummon";
    private static final String ARS_SUMMON = "com.hollingsworth.arsnouveau.api.entity.ISummon";
    private static final String GOETY_OWNED = "com.Polarice3.Goety.api.entities.IOwned";
    private static final String SLASHBLADE_SHOOTABLE = "mods.flammpfeil.slashblade.entity.IShootable";

    private static final Map<String, Optional<Class<?>>> OPTIONAL_TYPES = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> OWNER_CAPABLE_CACHE = new ConcurrentHashMap<>();

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

        Set<UUID> firstOwners = collectAffinityIds(first);
        Set<UUID> secondOwners = collectAffinityIds(second);
        if (firstOwners.isEmpty() || secondOwners.isEmpty()) {
            return false;
        }
        for (UUID id : firstOwners) {
            if (secondOwners.contains(id)) {
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
        return areFriendly(target, causing)
                || areFriendly(target, direct)
                || resolveResponsibleEntity(direct).map(owner -> areFriendly(target, owner)).orElse(false);
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
        if (isOptionalInstance(entity, IRONS_MAGIC_SUMMON)) {
            return invokeEntity(entity, "getSummoner");
        } else if (isOptionalInstance(entity, GOETY_OWNED)) {
            Entity owner = invokeEntity(entity, "getTrueOwner");
            return owner != null ? owner : invokeEntity(entity, "getMasterOwner");
        } else if (isOptionalInstance(entity, ARS_SUMMON)) {
            Entity owner = invokeEntity(entity, "getOwnerAlt");
            if (owner != null) {
                return owner;
            }
            return findEntity(entity, invokeUuid(entity, "getOwnerUUID"));
        } else if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwner();
        } else if (isOptionalInstance(entity, SLASHBLADE_SHOOTABLE)) {
            Entity shooter = invokeEntity(entity, "getShooter");
            return shooter != null ? shooter : invokeCommonOwner(entity);
        } else if (entity instanceof Mob) {
            return invokeCommonOwner(entity);
        } else if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner != null ? owner : invokeCommonOwner(entity);
        }
        return invokeCommonOwner(entity);
    }

    @Nullable
    private static UUID getDirectOwnerId(Entity entity) {
        if (entity instanceof EntityMaid maid) {
            return maid.getOwnerUUID();
        } else if (isOptionalInstance(entity, ARS_SUMMON)) {
            return invokeUuid(entity, "getOwnerUUID");
        } else if (isOptionalInstance(entity, GOETY_OWNED)) {
            return invokeUuid(entity, "getOwnerId");
        } else if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID();
        }
        UUID ownerId = invokeUuid(entity, "getOwnerUUID");
        return ownerId != null ? ownerId : invokeUuid(entity, "getOwnerId");
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
    private static Entity invokeCommonOwner(Entity entity) {
        Entity owner = invokeEntity(entity, "getOwner");
        if (owner != null) {
            return owner;
        }
        owner = invokeEntity(entity, "getCaster");
        if (owner != null) {
            return owner;
        }
        owner = invokeEntity(entity, "getSource");
        if (owner != null) {
            return owner;
        }
        return invokeEntity(entity, "getOwnerAlt");
    }

    @Nullable
    private static Entity invokeEntity(Entity entity, String methodName) {
        Object value = invokeNoArg(entity, methodName, Entity.class);
        return value instanceof Entity owner ? owner : null;
    }

    @Nullable
    private static UUID invokeUuid(Entity entity, String methodName) {
        return invokeUuid((Object) entity, methodName);
    }

    @Nullable
    private static UUID invokeUuid(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName, UUID.class);
        return value instanceof UUID uuid ? uuid : null;
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName, @Nullable Class<?> returnType) {
        Optional<Method> cached = METHODS.computeIfAbsent(
                new MethodKey(target.getClass(), methodName, returnType),
                MaidSpellAllyResolver::findNoArgMethod);
        if (cached.isEmpty()) {
            return null;
        }
        try {
            return cached.get().invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Optional<Method> findNoArgMethod(MethodKey key) {
        try {
            Method method = key.type().getMethod(key.name());
            if (isUsableMethod(method, key)) {
                return Optional.of(method);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }

        Class<?> type = key.type();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(key.name());
                if (isUsableMethod(method, key)) {
                    return Optional.of(method);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            type = type.getSuperclass();
        }
        return Optional.empty();
    }

    private static boolean isUsableMethod(Method method, MethodKey key) {
        if (method.getParameterCount() != 0) {
            return false;
        }
        if (key.returnType() != null && !isReturnTypeCompatible(key.returnType(), method.getReturnType())) {
            return false;
        }
        if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            method.setAccessible(true);
        }
        return true;
    }

    private static boolean isReturnTypeCompatible(Class<?> expected, Class<?> actual) {
        if (expected.isAssignableFrom(actual)) {
            return true;
        }
        return (expected == Boolean.class && actual == Boolean.TYPE)
                || (expected == Integer.class && actual == Integer.TYPE)
                || (expected == Long.class && actual == Long.TYPE)
                || (expected == Float.class && actual == Float.TYPE)
                || (expected == Double.class && actual == Double.TYPE);
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
                || Projectile.class.isAssignableFrom(type)
                || Player.class.isAssignableFrom(type)) {
            return true;
        }
        if (isTypeAssignableTo(type, IRONS_MAGIC_SUMMON)
                || isTypeAssignableTo(type, GOETY_OWNED)
                || isTypeAssignableTo(type, ARS_SUMMON)
                || isTypeAssignableTo(type, SLASHBLADE_SHOOTABLE)) {
            return true;
        }
        return hasSupportedOwnerAccessor(type);
    }

    private static boolean isTypeAssignableTo(Class<?> type, String className) {
        return OPTIONAL_TYPES.computeIfAbsent(className, MaidSpellAllyResolver::loadOptionalType)
                .map(optionalType -> optionalType.isAssignableFrom(type))
                .orElse(false);
    }

    private static boolean hasSupportedOwnerAccessor(Class<?> type) {
        String[] entityAccessors = {"getOwner", "getCaster", "getSource", "getOwnerAlt"};
        for (String methodName : entityAccessors) {
            if (METHODS.computeIfAbsent(
                    new MethodKey(type, methodName, Entity.class),
                    MaidSpellAllyResolver::findNoArgMethod).isPresent()) {
                return true;
            }
        }

        String[] uuidAccessors = {"getOwnerUUID", "getOwnerId"};
        for (String methodName : uuidAccessors) {
            if (METHODS.computeIfAbsent(
                    new MethodKey(type, methodName, UUID.class),
                    MaidSpellAllyResolver::findNoArgMethod).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private record MethodKey(Class<?> type, String name, @Nullable Class<?> returnType) {
    }
}
