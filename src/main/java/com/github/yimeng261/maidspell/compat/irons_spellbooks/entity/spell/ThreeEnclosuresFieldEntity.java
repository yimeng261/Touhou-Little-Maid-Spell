package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.ThreeEnclosuresSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 三垣连星的服务端场地状态。
 *
 * <p>实体位置是三角棱柱的中心，三个底面星位与三个顶面星位保持垂直对应。
 * 有目标时棱柱由外向内收缩；没有目标时则从约 6 格面积向外展开。</p>
 */
public class ThreeEnclosuresFieldEntity extends Entity {
    public static final double INITIAL_RADIUS = 10.0D;
    /** 等边三角形外接圆半径；对应底面面积约 6.0 格。 */
    public static final double FINAL_RADIUS = 2.15D;
    public static final double PRISM_HEIGHT = 8.0D;

    public static final int TELEGRAPH_TICKS = 30;
    public static final int SHRINK_TICKS = 180;
    public static final int FINAL_HOLD_TICKS = 40;
    public static final int TOTAL_TICKS = TELEGRAPH_TICKS + SHRINK_TICKS + FINAL_HOLD_TICKS;

    private static final float WALL_THICKNESS = 0.35F;
    private static final int DAMAGE_INTERVAL = 10;
    private static final int SOUND_INTERVAL = 16;
    private static final int SLOW_REFRESH_INTERVAL = 4;
    private static final DustParticleOptions SHRINK_DUST =
            new DustParticleOptions(new Vector3f(0.22F, 0.68F, 1.0F), 0.72F);
    private static final DustParticleOptions EXPAND_DUST =
            new DustParticleOptions(new Vector3f(0.72F, 0.28F, 1.0F), 0.72F);

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(ThreeEnclosuresFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANGLE =
            SynchedEntityData.defineId(ThreeEnclosuresFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BASE_Y =
            SynchedEntityData.defineId(ThreeEnclosuresFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TOP_Y =
            SynchedEntityData.defineId(ThreeEnclosuresFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_EXPANDING =
            SynchedEntityData.defineId(ThreeEnclosuresFieldEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private UUID casterUuid;
    @Nullable
    private LivingEntity cachedCaster;
    private int spellLevel = 3;
    private final Map<UUID, Integer> soundCooldowns = new HashMap<>();

    public ThreeEnclosuresFieldEntity(EntityType<? extends ThreeEnclosuresFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public ThreeEnclosuresFieldEntity(Level level, LivingEntity caster, Vec3 center,
                                      int spellLevel, boolean expanding) {
        this(IronsSpellbooksCompatEntities.THREE_ENCLOSURES.get(), level);
        this.cachedCaster = caster;
        this.casterUuid = caster.getUUID();
        this.spellLevel = Mth.clamp(spellLevel, 1, 5);
        this.setPos(center.x, center.y, center.z);
        this.entityData.set(DATA_RADIUS, (float) INITIAL_RADIUS);
        this.entityData.set(DATA_ANGLE, caster.getRandom().nextFloat() * Mth.TWO_PI);
        this.entityData.set(DATA_BASE_Y, (float) center.y);
        this.entityData.set(DATA_TOP_Y, (float) (center.y + PRISM_HEIGHT));
        this.entityData.set(DATA_EXPANDING, expanding);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }

        LivingEntity caster = getCaster();
        if (!IAuthoritativeHealth.combatAlive(caster)
                || caster.level() != level()
                || tickCount > TOTAL_TICKS) {
            discard();
            return;
        }

        float radius = currentRadius(tickCount);
        entityData.set(DATA_RADIUS, radius);
        entityData.set(DATA_ANGLE, entityData.get(DATA_ANGLE) + 0.006F);
        if (tickCount % SLOW_REFRESH_INTERVAL == 0) {
            applyFieldEffects(caster, radius);
        }
        if (tickCount % 4 == 0) {
            spawnStarParticles((ServerLevel) level(), radius);
        }
        for (Map.Entry<UUID, Integer> entry : soundCooldowns.entrySet()) {
            entry.setValue(entry.getValue() - 1);
        }
        soundCooldowns.values().removeIf(value -> value <= 0);
    }

    private float currentRadius(int age) {
        boolean expanding = entityData.get(DATA_EXPANDING);
        if (age <= TELEGRAPH_TICKS) {
            return (float) (expanding ? FINAL_RADIUS : INITIAL_RADIUS);
        }
        float progress = Mth.clamp((age - TELEGRAPH_TICKS) / (float) SHRINK_TICKS, 0.0F, 1.0F);
        return expanding
                ? (float) Mth.lerp(progress, FINAL_RADIUS, INITIAL_RADIUS)
                : (float) Mth.lerp(progress, INITIAL_RADIUS, FINAL_RADIUS);
    }

    private void applyFieldEffects(LivingEntity caster, double radius) {
        Vec3 center = position();
        AABB area = new AABB(center, center).inflate(radius + 2.0D, PRISM_HEIGHT + 1.0D, radius + 2.0D);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> isValidTarget(candidate, caster))) {
            boolean insideHeight = target.getY() >= getBaseY()
                    && target.getY() <= getTopY();
            boolean inside = insideHeight && isInsideTriangle(target.getX(), target.getZ(), radius);
            if (inside) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0,
                        false, true, true));
            }

            boolean nearHorizontalCap = target.getY() >= getBaseY() - 0.75D
                    && target.getY() <= getTopY() + 0.75D;
            if (!nearHorizontalCap) {
                continue;
            }
            double distanceToWall = distanceToTriangleWall(target.getX(), target.getZ(), radius);
            boolean touchingWall = !inside || distanceToWall <= WALL_THICKNESS;
            if (!touchingWall) {
                continue;
            }

            if (inside || distanceToWall <= WALL_THICKNESS + 0.75D) {
                pushTowardCenter(target);
            }
            if (tickCount % DAMAGE_INTERVAL == 0) {
                ThreeEnclosuresSpell spell = (ThreeEnclosuresSpell)
                        IronsSpellbooksCompatSpells.THREE_ENCLOSURES.get();
                float damage = spell.getWallDamage(spellLevel, caster);
                target.hurt(spell.getDamageSource(this, caster), damage);
            }
            if (!soundCooldowns.containsKey(target.getUUID())) {
                level().playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK,
                        SoundSource.HOSTILE, 0.65F, 0.75F + level().random.nextFloat() * 0.25F);
                soundCooldowns.put(target.getUUID(), SOUND_INTERVAL);
            }
        }
    }

    private boolean isValidTarget(LivingEntity target, LivingEntity caster) {
        return target != caster
                && IAuthoritativeHealth.combatAlive(target)
                && !target.isSpectator()
                && !(target instanceof Player player && player.isCreative())
                && !MaidSpellAllyResolver.areFriendly(caster, target);
    }

    private void pushTowardCenter(LivingEntity target) {
        Vec3 horizontal = position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return;
        }
        horizontal = horizontal.normalize().scale(0.08D);
        target.push(horizontal.x, 0.02D, horizontal.z);
        target.hurtMarked = true;
    }

    /** 返回三角形外接圆上的三个星位，顺时针角度由同步值确定。 */
    public Vec3[] getBaseVertices(float partialTicks) {
        double radius = getRadius(partialTicks);
        double angle = getAngle(partialTicks);
        Vec3[] vertices = new Vec3[3];
        for (int i = 0; i < vertices.length; i++) {
            double theta = angle + i * Mth.TWO_PI / 3.0D;
            vertices[i] = new Vec3(Math.cos(theta) * radius, 0.0D, Math.sin(theta) * radius);
        }
        return vertices;
    }

    public float getRadius(float partialTicks) {
        return entityData.get(DATA_RADIUS);
    }

    public float getAngle(float partialTicks) {
        return entityData.get(DATA_ANGLE);
    }

    public boolean isExpanding() {
        return entityData.get(DATA_EXPANDING);
    }

    public float getProgress(float partialTicks) {
        return Mth.clamp((tickCount + partialTicks - TELEGRAPH_TICKS) / (float) SHRINK_TICKS,
                0.0F, 1.0F);
    }

    public double getBaseY() {
        return entityData.get(DATA_BASE_Y);
    }

    public double getTopY() {
        return entityData.get(DATA_TOP_Y);
    }

    public int getRemainingTicks() {
        return Math.max(0, TOTAL_TICKS - tickCount);
    }

    public boolean isInsideTriangle(double x, double z, double radius) {
        Vec3[] vertices = getVertices(radius, entityData.get(DATA_ANGLE));
        double c0 = cross(vertices[1], vertices[0], x, z);
        double c1 = cross(vertices[2], vertices[1], x, z);
        double c2 = cross(vertices[0], vertices[2], x, z);
        return (c0 >= -1.0E-6D && c1 >= -1.0E-6D && c2 >= -1.0E-6D)
                || (c0 <= 1.0E-6D && c1 <= 1.0E-6D && c2 <= 1.0E-6D);
    }

    private double distanceToTriangleWall(double x, double z, double radius) {
        Vec3[] vertices = getVertices(radius, entityData.get(DATA_ANGLE));
        double minimum = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            Vec3 a = vertices[i];
            Vec3 b = vertices[(i + 1) % 3];
            double edgeX = b.x - a.x;
            double edgeZ = b.z - a.z;
            double length = Math.sqrt(edgeX * edgeX + edgeZ * edgeZ);
            double distance = Math.abs(edgeX * (z - a.z) - edgeZ * (x - a.x)) / length;
            minimum = Math.min(minimum, distance);
        }
        return minimum;
    }

    private Vec3[] getVertices(double radius, double angle) {
        Vec3[] vertices = new Vec3[3];
        for (int i = 0; i < vertices.length; i++) {
            double theta = angle + i * Mth.TWO_PI / 3.0D;
            vertices[i] = new Vec3(
                    getX() + Math.cos(theta) * radius,
                    getBaseY(),
                    getZ() + Math.sin(theta) * radius);
        }
        return vertices;
    }

    private static double cross(Vec3 a, Vec3 b, double x, double z) {
        return (a.x - b.x) * (z - b.z) - (a.z - b.z) * (x - b.x);
    }

    private void spawnStarParticles(ServerLevel server, double radius) {
        Vec3[] base = getBaseVertices(0.0F);
        for (Vec3 vertex : base) {
            server.sendParticles(ParticleTypes.END_ROD,
                    getX() + vertex.x, getBaseY() + 0.15D, getZ() + vertex.z,
                    1, 0.10D, 0.10D, 0.10D, 0.01D);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    getX() + vertex.x, getBaseY() + 0.15D, getZ() + vertex.z,
                    1, 0.08D, 0.08D, 0.08D, 0.02D);
            server.sendParticles(ParticleTypes.END_ROD,
                    getX() + vertex.x, getTopY(), getZ() + vertex.z,
                    1, 0.10D, 0.10D, 0.10D, 0.01D);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    getX() + vertex.x, getTopY(), getZ() + vertex.z,
                    1, 0.08D, 0.08D, 0.08D, 0.02D);
        }
        if (tickCount % 10 == 0) {
            server.sendParticles(ParticleTypes.ENCHANT,
                    getX(), getBaseY() + PRISM_HEIGHT * 0.5D, getZ(),
                    4, radius * 0.18D, PRISM_HEIGHT * 0.18D, radius * 0.18D, 0.035D);
            if (!isExpanding()) {
                server.sendParticles(ParticleTypes.END_ROD,
                        getX(), getBaseY() + 0.15D, getZ(),
                        2, 0.12D, 0.04D, 0.12D, 0.01D);
            }
        }

        // 每条棱上有一两颗移动的光点，看起来像能量沿棱柱流动；
        // 固定的 END_ROD 光点会让旧效果挤成一团白斑。
        if (tickCount % 2 == 0) {
            DustParticleOptions dust = isExpanding() ? EXPAND_DUST : SHRINK_DUST;
            Vec3[] top = new Vec3[3];
            for (int i = 0; i < 3; i++) {
                top[i] = base[i].add(0.0D, PRISM_HEIGHT, 0.0D);
            }
            for (int i = 0; i < 3; i++) {
                spawnMovingMote(server, dust, base[i], base[(i + 1) % 3], i * 0.21D);
                spawnMovingMote(server, dust, top[i], top[(i + 1) % 3], i * 0.21D + 0.5D);
                spawnMovingMote(server, dust, base[i], top[i], i * 0.21D + 0.25D);
            }
        }
    }

    private void spawnMovingMote(ServerLevel server, DustParticleOptions dust,
                                 Vec3 from, Vec3 to, double phase) {
        double progress = (tickCount * 0.035D + phase) % 1.0D;
        Vec3 point = from.lerp(to, progress);
        server.sendParticles(dust, getX() + point.x, getBaseY() + point.y, getZ() + point.z,
                1, 0.035D, 0.035D, 0.035D, 0.005D);
    }

    @Nullable
    private LivingEntity getCaster() {
        if (cachedCaster != null && !cachedCaster.isRemoved()) {
            return cachedCaster;
        }
        if (casterUuid != null && level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(casterUuid) instanceof LivingEntity living) {
            cachedCaster = living;
            return living;
        }
        return null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, (float) INITIAL_RADIUS);
        builder.define(DATA_ANGLE, 0.0F);
        builder.define(DATA_BASE_Y, 0.0F);
        builder.define(DATA_TOP_Y, (float) PRISM_HEIGHT);
        builder.define(DATA_EXPANDING, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (casterUuid != null) tag.putUUID("Caster", casterUuid);
        tag.putInt("SpellLevel", spellLevel);
        tag.putInt("Age", tickCount);
        tag.putFloat("Angle", entityData.get(DATA_ANGLE));
        tag.putDouble("BaseY", getBaseY());
        tag.putDouble("TopY", getTopY());
        tag.putBoolean("Expanding", isExpanding());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Caster")) casterUuid = tag.getUUID("Caster");
        spellLevel = Mth.clamp(tag.getInt("SpellLevel"), 1, 5);
        tickCount = tag.getInt("Age");
        entityData.set(DATA_ANGLE, tag.getFloat("Angle"));
        entityData.set(DATA_BASE_Y, tag.getFloat("BaseY"));
        entityData.set(DATA_TOP_Y, tag.getFloat("TopY"));
        entityData.set(DATA_EXPANDING, tag.getBoolean("Expanding"));
        entityData.set(DATA_RADIUS, currentRadius(tickCount));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
