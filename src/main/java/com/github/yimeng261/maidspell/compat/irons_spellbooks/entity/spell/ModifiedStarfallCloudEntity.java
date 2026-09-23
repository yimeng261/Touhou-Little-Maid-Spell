package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ModifiedStarfallCloudEntity extends Entity {
    private static final int DURATION_TICKS = 160;
    private static final int VOLLEY_INTERVAL_TICKS = 4;
    private static final int COMETS_PER_VOLLEY = 2;
    private static final double TARGET_RANGE = 20.0D;
    private static final double TARGET_RANGE_SQR = TARGET_RANGE * TARGET_RANGE;
    private static final double CONE_DOT_THRESHOLD = Math.cos(30.0D * Mth.DEG_TO_RAD);
    private static final float STORM_RADIUS = 6.0F;
    private static final Vec3 FALL_DIRECTION = new Vec3(0.15D, -0.85D, 0.0D).normalize();

    @Nullable
    private UUID casterUuid;
    @Nullable
    private LivingEntity cachedCaster;
    private float cometDamage;

    public ModifiedStarfallCloudEntity(EntityType<? extends ModifiedStarfallCloudEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public ModifiedStarfallCloudEntity(Level level, LivingEntity caster, float cometDamage) {
        this(IronsSpellbooksCompatEntities.MODIFIED_STARFALL_CLOUD.get(), level);
        this.casterUuid = caster.getUUID();
        this.cachedCaster = caster;
        this.cometDamage = cometDamage;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }

        LivingEntity caster = getCaster();
        if (caster instanceof com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity boss
            && !boss.isBattleActive()) {
            discard();
            return;
        }
        if (caster == null || !caster.isAlive() || caster.level() != level() || tickCount > DURATION_TICKS) {
            discard();
            return;
        }

        setPos(caster.position());
        if (tickCount % VOLLEY_INTERVAL_TICKS == 0) {
            LivingEntity target = findTarget(caster);
            for (int i = 0; i < COMETS_PER_VOLLEY; i++) {
                spawnComet(caster, target);
            }
        }
    }

    @Nullable
    private LivingEntity getCaster() {
        if (cachedCaster != null && !cachedCaster.isRemoved()) {
            return cachedCaster;
        }
        if (casterUuid != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(casterUuid);
            if (entity instanceof LivingEntity livingEntity) {
                cachedCaster = livingEntity;
                return livingEntity;
            }
        }
        return null;
    }

    @Nullable
    private LivingEntity findTarget(LivingEntity caster) {
        AABB searchArea = caster.getBoundingBox().inflate(TARGET_RANGE);
        List<LivingEntity> candidates = level().getEntitiesOfClass(
                LivingEntity.class,
                searchArea,
                target -> isValidTarget(caster, target));
        if (candidates.isEmpty()) {
            return null;
        }

        Vec3 eyePosition = caster.getEyePosition();
        Vec3 lookDirection = caster.getLookAngle().normalize();
        LivingEntity nearest = null;
        LivingEntity bestInCone = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        double bestConeDot = CONE_DOT_THRESHOLD;
        double bestConeDistanceSqr = Double.MAX_VALUE;

        for (LivingEntity candidate : candidates) {
            Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(eyePosition);
            double distanceSqr = toCandidate.lengthSqr();
            if (distanceSqr < nearestDistanceSqr) {
                nearest = candidate;
                nearestDistanceSqr = distanceSqr;
            }
            if (distanceSqr < 1.0E-6D) {
                return candidate;
            }

            double dot = lookDirection.dot(toCandidate.scale(1.0D / Math.sqrt(distanceSqr)));
            if (dot >= CONE_DOT_THRESHOLD
                    && (bestInCone == null
                    || dot > bestConeDot + 1.0E-6D
                    || Math.abs(dot - bestConeDot) <= 1.0E-6D && distanceSqr < bestConeDistanceSqr)) {
                bestInCone = candidate;
                bestConeDot = dot;
                bestConeDistanceSqr = distanceSqr;
            }
        }

        return bestInCone != null ? bestInCone : nearest;
    }

    private boolean isValidTarget(LivingEntity caster, LivingEntity target) {
        return target != caster
                && target.isAlive()
                && !target.isSpectator()
                && target.canBeHitByProjectile()
                && target.distanceToSqr(caster) <= TARGET_RANGE_SQR
                && !DamageSources.isFriendlyFireBetween(caster, target)
                && !MaidSpellAllyResolver.areFriendly(caster, target);
    }

    private void spawnComet(LivingEntity caster, @Nullable LivingEntity target) {
        float distance = caster.getRandom().nextFloat() * STORM_RADIUS;
        float angle = caster.getRandom().nextFloat() * Mth.PI * 2.0F;
        Vec3 spawnTarget = Utils.moveToRelativeGroundLevel(
                level(),
                caster.position().add(new Vec3(0.0D, 0.0D, distance).yRot(angle)),
                3).add(0.0D, 0.5D, 0.0D);
        Vec3 spawn = Utils.raycastForBlock(
                level(),
                spawnTarget,
                spawnTarget.add(FALL_DIRECTION.scale(-12.0D)),
                ClipContext.Fluid.NONE).getLocation().add(FALL_DIRECTION);

        ModifiedStarfallCometEntity comet = new ModifiedStarfallCometEntity(level(), caster);
        comet.setPos(spawn.add(-1.0D, 0.0D, 0.0D));

        Vec3 initialDirection = FALL_DIRECTION;
        if (target != null) {
            Vec3 toTarget = target.getBoundingBox().getCenter()
                    .add(target.getDeltaMovement())
                    .subtract(comet.position());
            if (toTarget.lengthSqr() > 1.0E-6D) {
                initialDirection = toTarget.normalize();
            }
            comet.setHomingTarget(target);
        }

        comet.shoot(initialDirection, 0.075F);
        comet.setDamage(cometDamage);
        comet.setExplosionRadius(2.0F);
        level().addFreshEntity(comet);

        level().playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, 3.0F, 0.7F + Utils.random.nextFloat() * 0.3F);
        MagicManager.spawnParticles(level(), ParticleHelper.COMET_FOG, spawn.x, spawn.y, spawn.z,
                1, 1.0D, 1.0D, 1.0D, 1.0D, false);
        MagicManager.spawnParticles(level(), ParticleHelper.COMET_FOG, spawn.x, spawn.y, spawn.z,
                1, 1.0D, 1.0D, 1.0D, 1.0D, true);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (casterUuid != null) {
            tag.putUUID("Caster", casterUuid);
        }
        tag.putFloat("CometDamage", cometDamage);
        tag.putInt("Age", tickCount);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.hasUUID("Caster")) {
            casterUuid = tag.getUUID("Caster");
        }
        cometDamage = tag.getFloat("CometDamage");
        tickCount = tag.getInt("Age");
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
