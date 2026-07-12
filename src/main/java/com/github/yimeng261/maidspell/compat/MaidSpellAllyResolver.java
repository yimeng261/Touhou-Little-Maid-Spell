package com.github.yimeng261.maidspell.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.ftbteams.FTBTeamsCompat;
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

    private static final String IRONS_MAGIC_SUMMON = "io.redspace.ironsspellbooks.entity.mobs.MagicSummon";
    private static final String ARS_SUMMON = "com.hollingsworth.arsnouveau.api.entity.ISummon";
    private static final String GOETY_OWNED = "com.Polarice3.Goety.api.entities.IOwned";
    private static final String SLASHBLADE_SHOOTABLE = "mods.flammpfeil.slashblade.entity.IShootable";
    private static final String WIZARDRY_SERVICES = "com.binaris.wizardry.core.platform.Services";

    private static final Map<String, Optional<Class<?>>> OPTIONAL_TYPES = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> OWNER_CAPABLE_CACHE = new ConcurrentHashMap<>();
    private static final Optional<Object> WIZARDRY_OBJECT_DATA = loadStaticField(WIZARDRY_SERVICES, "OBJECT_DATA");

    private MaidSpellAllyResolver() {
    }

    public static boolean areFriendly(@Nullable Entity first, @Nullable Entity second) {
        if (first == null || second == null || first == second) {
            return first != null && first == second;
        }
        if (!couldHaveOwner(first) && !couldHaveOwner(second)) {
            return false;
        }
        if (hasExplicitTeamAlliance(first, second)) {
            return true;
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
        return FTBTeamsCompat.areFriendly(firstOwners, secondOwners);
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
        if (entity instanceof Player) {
            return null;
        } else if (isOptionalInstance(entity, IRONS_MAGIC_SUMMON)) {
            return invokeOptionalEntity(entity, IRONS_MAGIC_SUMMON, "getSummoner");
        } else if (isOptionalInstance(entity, GOETY_OWNED)) {
            Entity owner = invokeOptionalEntity(entity, GOETY_OWNED, "getTrueOwner");
            return owner != null ? owner : invokeOptionalEntity(entity, GOETY_OWNED, "getMasterOwner");
        } else if (isOptionalInstance(entity, ARS_SUMMON)) {
            Entity owner = invokeOptionalEntity(entity, ARS_SUMMON, "getOwnerAlt");
            if (owner != null) {
                return owner;
            }
            return findEntity(entity, invokeOptionalUuid(entity, ARS_SUMMON, "getOwnerUUID"));
        } else if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwner();
        } else if (isOptionalInstance(entity, SLASHBLADE_SHOOTABLE)) {
            return invokeOptionalEntity(entity, SLASHBLADE_SHOOTABLE, "getShooter");
        } else if (entity instanceof Mob mob) {
            return getWizardryMinionOwner(mob);
        } else if (entity instanceof Projectile projectile) {
            return projectile.getOwner();
        }
        return null;
    }

    @Nullable
    private static UUID getDirectOwnerId(Entity entity) {
        if (entity instanceof EntityMaid maid) {
            return maid.getOwnerUUID();
        } else if (isOptionalInstance(entity, ARS_SUMMON)) {
            return invokeOptionalUuid(entity, ARS_SUMMON, "getOwnerUUID");
        } else if (isOptionalInstance(entity, GOETY_OWNED)) {
            return invokeOptionalUuid(entity, GOETY_OWNED, "getOwnerId");
        } else if (entity instanceof Player) {
            return null;
        } else if (entity instanceof Mob mob) {
            UUID ownerId = getWizardryMinionOwnerId(mob);
            if (ownerId != null) {
                return ownerId;
            }
        } else if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID();
        } else if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
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

    private static Optional<Object> loadStaticField(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className, false, MaidSpellAllyResolver.class.getClassLoader());
            return Optional.ofNullable(type.getField(fieldName).get(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    @Nullable
    private static Entity getWizardryMinionOwner(Mob mob) {
        Object data = getWizardryMinionData(mob);
        if (data == null || !invokeBoolean(data, "isSummoned")) {
            return null;
        }
        Object owner = invokeNoArg(data, "getOwner");
        return owner instanceof Entity entity ? entity : findEntity(mob, invokeUuid(data, "getOwnerUUID"));
    }

    @Nullable
    private static UUID getWizardryMinionOwnerId(Mob mob) {
        Object data = getWizardryMinionData(mob);
        if (data == null || !invokeBoolean(data, "isSummoned")) {
            return null;
        }
        return invokeUuid(data, "getOwnerUUID");
    }

    @Nullable
    private static Object getWizardryMinionData(Mob mob) {
        return WIZARDRY_OBJECT_DATA
                .map(objectData -> invokeNoArg(objectData, "getMinionData", Mob.class, mob))
                .orElse(null);
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
    private static UUID invokeUuid(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName, UUID.class);
        return value instanceof UUID uuid ? uuid : null;
    }

    @Nullable
    private static Object invokeOptionalNoArg(Object target, String className, String methodName, @Nullable Class<?> returnType) {
        Optional<Class<?>> type = OPTIONAL_TYPES.computeIfAbsent(className, MaidSpellAllyResolver::loadOptionalType);
        if (type.isEmpty() || !type.get().isInstance(target)) {
            return null;
        }
        Optional<Method> cached = METHODS.computeIfAbsent(new MethodKey(type.get(), methodName, returnType, null), MaidSpellAllyResolver::findPublicNoArgMethod);
        if (cached.isEmpty()) {
            return null;
        }
        try {
            return cached.get().invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName, Boolean.class);
        return value instanceof Boolean result && result;
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName) {
        return invokeNoArg(target, methodName, null);
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName, @Nullable Class<?> returnType) {
        Optional<Method> cached = METHODS.computeIfAbsent(new MethodKey(target.getClass(), methodName, returnType, null), MaidSpellAllyResolver::findNoArgMethod);
        if (cached.isEmpty()) {
            return null;
        }
        try {
            return cached.get().invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName, Class<?> parameterType, Object parameter) {
        Optional<Method> cached = METHODS.computeIfAbsent(new MethodKey(target.getClass(), methodName, null, parameterType), MaidSpellAllyResolver::findMethod);
        if (cached.isEmpty()) {
            return null;
        }
        try {
            return cached.get().invoke(target, parameter);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Optional<Method> findNoArgMethod(MethodKey key) {
        return findMethod(key);
    }

    private static Optional<Method> findPublicNoArgMethod(MethodKey key) {
        try {
            Method method = key.type().getMethod(key.name());
            if (isUsableMethod(method, key)) {
                return Optional.of(method);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Method> findMethod(MethodKey key) {
        return findExactMethod(key);
    }

    private static Optional<Method> findExactMethod(MethodKey key) {
        Class<?> type = key.type();
        while (type != null && type != Object.class) {
            try {
                Method method = key.parameterType() == null ? type.getDeclaredMethod(key.name()) : type.getDeclaredMethod(key.name(), key.parameterType());
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
        if (key.parameterType() == null) {
            if (method.getParameterCount() != 0) {
                return false;
            }
        } else if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].isAssignableFrom(key.parameterType())) {
            return false;
        }
        Class<?> returnType = key.returnType();
        if (returnType != null && !isReturnTypeCompatible(returnType, method.getReturnType())) {
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

    private static boolean checkOwnerCapable(Class<?> clazz) {
        if (EntityMaid.class.isAssignableFrom(clazz)
                || OwnableEntity.class.isAssignableFrom(clazz)
                || Projectile.class.isAssignableFrom(clazz)
                || Player.class.isAssignableFrom(clazz)) {
            return true;
        }
        if (isTypeAssignableTo(clazz, IRONS_MAGIC_SUMMON)
                || isTypeAssignableTo(clazz, GOETY_OWNED)
                || isTypeAssignableTo(clazz, ARS_SUMMON)
                || isTypeAssignableTo(clazz, SLASHBLADE_SHOOTABLE)) {
            return true;
        }
        if (WIZARDRY_OBJECT_DATA.isPresent() && Mob.class.isAssignableFrom(clazz)) {
            return true;
        }
        return false;
    }

    private static boolean isTypeAssignableTo(Class<?> clazz, String interfaceName) {
        return OPTIONAL_TYPES.computeIfAbsent(interfaceName, MaidSpellAllyResolver::loadOptionalType)
                .map(type -> type.isAssignableFrom(clazz))
                .orElse(false);
    }

    private record MethodKey(Class<?> type, String name, @Nullable Class<?> returnType, @Nullable Class<?> parameterType) {
    }
}
