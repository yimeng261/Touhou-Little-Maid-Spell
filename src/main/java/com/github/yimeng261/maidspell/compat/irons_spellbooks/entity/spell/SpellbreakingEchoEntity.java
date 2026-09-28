package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class SpellbreakingEchoEntity extends BlackHole {
    /** 球心位于施法者眼睛上方 5 格。 */
    public static final double HOVER_ABOVE_EYES = 5.0D;

    /** 实体坐标在球底，故球心高度要减去半径；初始落点与跟随共用此换算。 */
    public static double placementY(double eyeY, double radius) {
        return eyeY + HOVER_ABOVE_EYES - radius;
    }

    /** 波纹间隔（tick）。20 = 每秒一次，原来是 40（两秒）。 */
    private static final int WAVE_INTERVAL = 20;

    /** 一次波纹从内扫到外要多少 tick。12 tick = 0.6 秒，剩下的 8 tick 是间隔。 */
    private static final int WAVE_TICKS = 12;

    /** 球壳扫到效果半径的 1.2 倍，以覆盖施法者脚下。 */
    private static final double WAVE_REACH_MULTIPLIER = 1.2D;

    private static final DustParticleOptions WAVE_PARTICLE =
            new DustParticleOptions(new Vector3f(0.55F, 0.57F, 0.59F), 1.0F);

    private int durationTicks = 80;

    /** 效果范围独立于球体半径，避免扩大 ISS 的碰撞盒。 */
    private double effectRadius = 3.0D;

    public SpellbreakingEchoEntity(EntityType<? extends SpellbreakingEchoEntity> type, Level level) {
        super(type, level);
    }

    public SpellbreakingEchoEntity(Level level, LivingEntity owner) {
        super(IronsSpellbooksCompatEntities.SPELLBREAKING_ECHO.get(), level);
        setOwner(owner);
    }

    public void setDurationTicks(int ticks) {
        durationTicks = ticks;
    }

    /** 作用范围（法术在 {@code onCast} 里给），排斥场与波纹都用它。 */
    public void setEffectRadius(double radius) {
        effectRadius = radius;
    }

    public double getEffectRadius() {
        return effectRadius;
    }

    /** 球心：包围盒底面在 {@code position()}，而渲染出来的球以包围盒中心为心。 */
    private Vec3 sphereCenter() {
        return position().add(0.0D, getRadius(), 0.0D);
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            Entity owner = getOwner();
            if (!(owner instanceof LivingEntity caster)
                    || !IAuthoritativeHealth.combatAlive(caster) || caster.level() != level()) {
                discard();
                return;
            }
            follow(caster);
            super.tick();
            if (isRemoved()) {
                return;
            }
            if (tickCount >= durationTicks) {
                discard();
                return;
            }
            repel(caster);
            int waveTick = (tickCount - 1) % WAVE_INTERVAL;
            if (waveTick < WAVE_TICKS) {
                wave(caster, waveTick);
            }
        } else {
            super.tick();
        }
    }

    /** 跟随施法者前清零自身速度，并用球心高度换算目标位置。 */
    private void follow(LivingEntity caster) {
        Vec3 velocity = getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-8D) {
            setDeltaMovement(Vec3.ZERO);
            hurtMarked = true;
        }
        Vec3 eyes = caster.getEyePosition();
        Vec3 target = new Vec3(eyes.x, placementY(eyes.y, getRadius()), eyes.z);
        if (position().distanceToSqr(target) > 1.0E-8D) {
            setPos(target.x, target.y, target.z);
            resetFallDistance();
        }
    }

    /**
     * 把作用范围内的法术造物往外推。
     *
     * <p>中心是施法者<b>包围盒中心</b>而不是脚底：她要护的是自己这一团，
     * 贴在脚下的环反而漏掉身体高度上的弹射物。
     */
    private void repel(LivingEntity caster) {
        Vec3 center = caster.getBoundingBox().getCenter();
        double radius = effectRadius;
        AABB area = new AABB(center, center).inflate(radius);
        for (Entity entity : level().getEntities(this, area, e -> isSpellTarget(e, caster))) {
            Vec3 away = entity.getBoundingBox().getCenter().subtract(center);
            double distance = away.length();
            if (distance > radius + entity.getBbWidth() * 0.5D) {
                continue;
            }
            if (distance < 1.0E-4D) {
                away = new Vec3(0.0D, 1.0D, 0.0D);
                distance = 1.0D;
            }
            double strength = 0.15D + 0.35D * (1.0D - Math.min(distance / radius, 1.0D));
            entity.setDeltaMovement(entity.getDeltaMovement().add(away.scale(strength / distance)));
            entity.hurtMarked = true;
        }
    }

    /** 每秒向外扫一层球壳，用实体包围盒与壳体相交判定命中。 */
    private void wave(LivingEntity caster, int age) {
        double maxRadius = effectRadius * WAVE_REACH_MULTIPLIER;
        double innerRadius = maxRadius * age / WAVE_TICKS;
        double outerRadius = maxRadius * (age + 1) / WAVE_TICKS;
        Vec3 center = sphereCenter();
        double searchRadius = outerRadius + 1.0D;
        AABB area = new AABB(center.x - searchRadius, center.y - searchRadius, center.z - searchRadius,
                center.x + searchRadius, center.y + searchRadius, center.z + searchRadius);
        for (Entity entity : level().getEntities(this, area, e -> isSpellTarget(e, caster))) {
            AABB box = entity.getBoundingBox();
            double nearest = distanceToBox(center, box);
            double farthest = farthestCornerDistance(center, box);
            if (nearest <= outerRadius && farthest >= innerRadius) {
                removeSpellTarget(entity);
            }
        }
        if (level() instanceof ServerLevel server) {
            spawnShellParticles(server, center, outerRadius);
        }
    }

    /** 点到包围盒的最近距离（点在盒内时为 0）。 */
    private static double distanceToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0D), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0D), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0D), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 包围盒上离该点最远的那个角到它的距离。 */
    private static double farthestCornerDistance(Vec3 point, AABB box) {
        double dx = Math.max(Math.abs(box.minX - point.x), Math.abs(box.maxX - point.x));
        double dy = Math.max(Math.abs(box.minY - point.y), Math.abs(box.maxY - point.y));
        double dz = Math.max(Math.abs(box.minZ - point.z), Math.abs(box.maxZ - point.z));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 把球壳画出来：按纬度分几圈，每圈一串点。
     *
     * <p>为什么不铺满整个球面：{@code sendParticles} <b>一次调用一个数据包</b>，
     * 铺满（每格 10+ 个点，半径 8 时两百多个点）等于每 tick 两百多个包。
     * 分圈能把总量压在几十个点以内，看上去仍然是一层向外扩的壳。
     */
    private void spawnShellParticles(ServerLevel server, Vec3 center, double radius) {
        if (radius <= 0.05D) {
            return;
        }
        int rings = 4;
        for (int ring = 0; ring < rings; ring++) {
            double latitude = -Math.PI / 2.0D + Math.PI * (ring + 0.5D) / rings;
            double ringRadius = radius * Math.cos(latitude);
            double y = center.y + radius * Math.sin(latitude);
            int count = Mth.clamp((int) (ringRadius * 3.0D), 4, 12);
            for (int i = 0; i < count; i++) {
                double angle = 2.0D * Math.PI * i / count;
                server.sendParticles(WAVE_PARTICLE,
                        center.x + Math.cos(angle) * ringRadius, y,
                        center.z + Math.sin(angle) * ringRadius,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 对弹射物、召唤物和非生物法术生效，但保留施法者自己的造物。 */
    public static boolean isSpellTarget(Entity entity, Entity caster) {
        // RootEntity is a LivingEntity because it carries the rooted target as a passenger,
        // but it is itself an AntiMagicSusceptible spell construct and must be cleared.
        if (entity instanceof RootEntity) {
            return true;
        }
        if (entity instanceof IMagicSummon summon) {
            return summon.getSummoner() != caster;
        }
        if (entity instanceof Projectile projectile) {
            return projectile.getOwner() != caster;
        }
        return entity instanceof AntiMagicSusceptible && !(entity instanceof LivingEntity);
    }

    private static void removeSpellTarget(Entity entity) {
        if (entity instanceof RootEntity root) {
            root.removeRoot();
        } else {
            entity.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EchoDuration", durationTicks);
        tag.putDouble("EchoRadius", effectRadius);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("EchoDuration")) {
            durationTicks = tag.getInt("EchoDuration");
        }
        if (tag.contains("EchoRadius")) {
            effectRadius = tag.getDouble("EchoRadius");
        }
    }
}
