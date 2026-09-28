package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跟随施法者的小型黑洞，继承 ISS 的吸附逻辑。
 * 原版追踪名单不可由子类访问，吸附对象由 {@code CompanionBlackHoleFilterMixin} 过滤。
 */
public class CompanionBlackHoleEntity extends BlackHole {

    /** 球心固定在施法者眼睛上方 5 格，与法术等级无关。 */
    public static final double HOVER_ABOVE_EYES = 5.0D;

    /** 实体坐标在球底，因此球心高度须减去半径；初始落点和跟随共用此换算。 */
    public static double placementY(double eyeY, double radius) {
        return eyeY + HOVER_ABOVE_EYES - radius;
    }

    /** 吸力范围为半径的两倍，与原版黑洞一致。 */
    private static final double SUCTION_REACH_MULTIPLIER = 2.0D;

    /** 线性衰减吸力的最大加速度。 */
    private static final double SUCTION_ACCELERATION = 0.3D;

    /** 候选查询的外扩距离，也是可信移动线段的最大长度。 */
    private static final double MAX_SANE_STEP = 8.0D;

    /** 非弹射物的 ISS 法术实体无共同接口，用包名识别以兼容不同版本。 */
    private static final String ISS_SPELL_ENTITY_PACKAGE = "io.redspace.ironsspellbooks.entity.spells.";

    /** 缓存不同 ISS 实体的 getOwner/getOwnerUUID 反射结果。 */
    private static final Map<Class<?>, OwnerAccessor> OWNER_ACCESSORS = new ConcurrentHashMap<>();

    /** 一个能取出施法者的无参方法，以及它返回的是 {@code Entity} 还是 {@code UUID}。 */
    private record OwnerAccessor(Method method, boolean returnsUuid) {
        /** 找不到可用方法时的占位，{@code method() == null} 即「查不出主人」。 */
        static final OwnerAccessor NONE = new OwnerAccessor(null, false);
    }

    public CompanionBlackHoleEntity(EntityType<? extends CompanionBlackHoleEntity> entityType, Level level) {
        super(entityType, level);
    }

    public CompanionBlackHoleEntity(Level level, LivingEntity owner) {
        super(IronsSpellbooksCompatEntities.COMPANION_BLACK_HOLE.get(), level);
        this.setOwner(owner);
    }

    /** 服务端依次跟随、执行原版吸附、加强吸力并处理球体碰撞。 */
    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            followOwner();
            super.tick();
            applySuction();
            consumeContactingProjectiles();
            return;
        }
        super.tick();
    }

    /**
     * 原版四次方衰减过弱，额外给敌方弹射物施加线性衰减吸力。
     * 使用 push 同步速度；销毁统一由球体碰撞处理。
     */
    private void applySuction() {
        double radius = this.getRadius();
        if (radius <= 0.0D) {
            return;
        }
        Entity caster = this.getOwner();
        if (caster == null) {
            return;
        }
        Vec3 center = this.position().add(0.0D, radius, 0.0D);
        double reach = radius * SUCTION_REACH_MULTIPLIER;
        AABB area = new AABB(
                center.x - reach, center.y - reach, center.z - reach,
                center.x + reach, center.y + reach, center.z + reach);

        for (Entity entity : this.level().getEntities(this, area,
                candidate -> candidate instanceof Projectile projectile
                             && projectile.getOwner() != caster)) {
            Vec3 toCenter = center.subtract(entity.position());
            double distance = toCenter.length();
            if (distance > reach) {
                continue;
            }
            if (distance < 1.0E-4D) {
                // 球心处没有方向，避免除零。
                entity.push(0.0D, SUCTION_ACCELERATION, 0.0D);
                continue;
            }
            double strength = SUCTION_ACCELERATION * (1.0D - distance / reach);
            entity.push(toCenter.x / distance * strength,
                    toCenter.y / distance * strength,
                    toCenter.z / distance * strength);
        }
    }

    /**
     * 消除穿过球体的弹射物和 ISS 法术实体。
     * 查询范围外扩以捕获一 tick 内穿球而过的高速弹体，最终按移动线段判定。
     */
    private void consumeContactingProjectiles() {
        // 无法确认施法者时不消除实体，以免误伤友方。
        Entity caster = this.getOwner();
        if (caster == null || caster.isRemoved()) {
            return;
        }
        double radius = this.getRadius();
        if (radius <= 0.0D) {
            return;
        }
        // position() 在包围盒底面。
        Vec3 center = this.position().add(0.0D, radius, 0.0D);
        double reach = radius + MAX_SANE_STEP;

        // 按球心外扩，避免漏掉高速穿越的弹体。
        AABB search = new AABB(
                center.x - reach, center.y - reach, center.z - reach,
                center.x + reach, center.y + reach, center.z + reach);

        for (Entity candidate : this.level().getEntities(this, search)) {
            tryConsume(candidate, caster, center, radius);
        }
    }

    /** 多部件法术按部件判碰撞，但销毁父实体。 */
    private void tryConsume(Entity candidate, Entity caster, Vec3 center, double radius) {
        Entity target = candidate;
        if (candidate instanceof PartEntity<?> part) {
            target = part.getParent();
            if (target == null) {
                return;
            }
        }
        if (target == this || target.isRemoved()) {
            return;
        }
        if (target instanceof Projectile projectile) {
            // 保留施法者自己的弹射物。
            if (projectile.getOwner() == caster) {
                return;
            }
        } else {
            if (!isSpellEntity(target)) {
                return;
            }
            // 仅消除能确认归属的敌方法术。
            Entity spellOwner = resolvedOwner(target);
            if (spellOwner == null || spellOwner == caster) {
                return;
            }
        }
        // 部件参与几何判定，父实体承担销毁。
        if (crossesSphere(candidate, center, radius)) {
            target.discard();
        }
    }

    /** 排除召唤生物和多部件实体的部件；部件归属由父实体处理。 */
    private static boolean isSpellEntity(Entity entity) {
        return !(entity instanceof LivingEntity)
               && !(entity instanceof PartEntity<?>)
               && entity.getClass().getName().startsWith(ISS_SPELL_ENTITY_PACKAGE);
    }

    /** 查不到施法者时返回 null；无主法术可能是友方护盾，必须放过。 */
    private static Entity resolvedOwner(Entity entity) {
        OwnerAccessor accessor = OWNER_ACCESSORS.computeIfAbsent(
                entity.getClass(), CompanionBlackHoleEntity::findOwnerAccessor);
        Method method = accessor.method();
        if (method == null) {
            return null;
        }
        Object raw;
        try {
            raw = method.invoke(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 反射失败按归属不明处理。
            return null;
        }
        if (accessor.returnsUuid()) {
            // UUID 查找只在服务端可用。
            if (raw instanceof UUID uuid && entity.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getEntity(uuid);
            }
            return null;
        }
        return raw instanceof Entity owner ? owner : null;
    }

    /** 优先取实体主人，再尝试 UUID；忽略不支持的返回类型。 */
    private static OwnerAccessor findOwnerAccessor(Class<?> type) {
        for (String name : new String[] {"getOwner", "getOwnerUUID"}) {
            Method method;
            try {
                method = type.getMethod(name);
            } catch (NoSuchMethodException e) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returns = method.getReturnType();
            if (Entity.class.isAssignableFrom(returns)) {
                return new OwnerAccessor(method, false);
            }
            if (UUID.class.isAssignableFrom(returns)) {
                return new OwnerAccessor(method, true);
            }
        }
        return OwnerAccessor.NONE;
    }

    /**
     * 用上一 tick 到当前点的线段判球体碰撞，避免高速弹体穿透。
     * 步长异常时只看当前位置，防止旧坐标失真导致误判。
     */
    private static boolean crossesSphere(Entity projectile, Vec3 center, double radius) {
        Vec3 to = projectile.position();
        Vec3 from = new Vec3(projectile.xOld, projectile.yOld, projectile.zOld);
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        double radiusSqr = radius * radius;

        if (lengthSqr > MAX_SANE_STEP * MAX_SANE_STEP) {
            return to.distanceToSqr(center) <= radiusSqr;
        }

        double t = 0.0D;
        if (lengthSqr > 1.0E-8D) {
            t = center.subtract(from).dot(segment) / lengthSqr;
            t = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
        }
        Vec3 closest = from.add(segment.scale(t));
        return closest.distanceToSqr(center) <= radiusSqr;
    }

    /**
     * 跟随前清零外力造成的速度，否则原版 tick 会让黑洞持续漂移。
     * 仅在速度变化时同步给客户端。
     */
    private void followOwner() {
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-8D) {
            this.setDeltaMovement(Vec3.ZERO);
            this.hurtMarked = true;
        }
        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity living) || !IAuthoritativeHealth.combatAlive(living)
            || living.level() != this.level()) {
            return;
        }
        Vec3 target = desiredPosition(living);
        if (this.position().distanceToSqr(target) <= 1.0E-8D) {
            return;
        }
        this.setPos(target.x, target.y, target.z);
        // 跟着走的东西不该攒下落距离，否则她把它带上天之后它会一路"摔"下来。
        this.resetFallDistance();
    }

    /**
     * 跟随目标点：<b>球心</b>固定在施法者眼睛上方 {@link #HOVER_ABOVE_EYES} 格。
     *
     * <p>返回的是<b>实体坐标</b>（包围盒底面），不是球心 —— 换算见 {@link #placementY}。
     * 半径每 tick 现取，所以等级变了、或者原版改了半径，高度都不会跑偏。
     */
    private Vec3 desiredPosition(LivingEntity owner) {
        Vec3 eyes = owner.getEyePosition();
        return new Vec3(eyes.x, placementY(eyes.y, this.getRadius()), eyes.z);
    }
}
