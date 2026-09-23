package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import io.redspace.ironsspellbooks.api.entity.NoKnockbackProjectile;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.function.Supplier;

public class WinefoxSwordProjectileEntity extends AbstractMagicProjectile
        implements GeoEntity, NoKnockbackProjectile {
    private static final EntityDataAccessor<Boolean> DATA_PLANTED =
            SynchedEntityData.defineId(WinefoxSwordProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ROLL =
            SynchedEntityData.defineId(WinefoxSwordProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PLANTED_DIRECTION_X =
            SynchedEntityData.defineId(WinefoxSwordProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PLANTED_DIRECTION_Y =
            SynchedEntityData.defineId(WinefoxSwordProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PLANTED_DIRECTION_Z =
            SynchedEntityData.defineId(WinefoxSwordProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LANDING_Y =
            SynchedEntityData.defineId(WinefoxSwordProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final int PLANTED_LIFETIME = 80;
    private static final int DAMAGE_INTERVAL = 4;
    private static final double PULL_STRENGTH = 0.035D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int plantedTicks;

    public WinefoxSwordProjectileEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        setNoGravity(false);
    }

    public WinefoxSwordProjectileEntity(Level level, LivingEntity owner) {
        this(IronsSpellbooksCompatEntities.WINEFOX_SWORD_PROJECTILE.get(), level);
        setOwner(owner);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PLANTED, false);
        entityData.define(DATA_ROLL, 0.0F);
        entityData.define(DATA_PLANTED_DIRECTION_X, 0.0F);
        entityData.define(DATA_PLANTED_DIRECTION_Y, -1.0F);
        entityData.define(DATA_PLANTED_DIRECTION_Z, 0.0F);
        entityData.define(DATA_LANDING_Y, Float.NaN);
    }

    @Override
    public void tick() {
        if (!isPlanted()) {
            super.tick();
            return;
        }

        baseTick();
        if (level().isClientSide) {
            if (tickCount % 3 == 0) {
                trailParticles();
            }
            return;
        }

        plantedTicks++;
        if (plantedTicks % DAMAGE_INTERVAL == 0) {
            damageNearbyEntities();
        }
        if (plantedTicks >= PLANTED_LIFETIME) {
            discard();
        }
    }

    public boolean isPlanted() {
        return entityData.get(DATA_PLANTED);
    }

    public void setRoll(float roll) {
        entityData.set(DATA_ROLL, roll);
    }

    public float getRoll() {
        return entityData.get(DATA_ROLL);
    }

    public Vec3 getPlantedDirection() {
        return new Vec3(
                entityData.get(DATA_PLANTED_DIRECTION_X),
                entityData.get(DATA_PLANTED_DIRECTION_Y),
                entityData.get(DATA_PLANTED_DIRECTION_Z));
    }

    /** Sets the horizontal plane at which this sword should land instead of stopping at a block. */
    public void setLandingY(double landingY) {
        entityData.set(DATA_LANDING_Y, (float) landingY);
    }

    private boolean hasLandingPlane() {
        return !Float.isNaN(entityData.get(DATA_LANDING_Y));
    }

    private double getLandingY() {
        return entityData.get(DATA_LANDING_Y);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = getOwner();
        return super.canHitEntity(entity)
                && (owner == null || !MaidSpellAllyResolver.areFriendly(owner, entity));
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (!level().isClientSide) {
            applyContactEffect(hitResult.getEntity());
        }
    }

    @Override
    protected void onHit(@NotNull HitResult hitResult) {
        super.onHit(hitResult);
        if (!level().isClientSide && hitResult.getType() == HitResult.Type.BLOCK && !isPlanted()) {
            plantAt(hitResult.getLocation());
        }
    }

    /**
     * Sword Prison projectiles are intentionally allowed to pass through blocks on their way to
     * the target plane. Entity hit detection is retained, so a target under a low ceiling can
     * still be hit before the sword reaches the plane.
     */
    @Override
    public void handleHitDetection() {
        if (!hasLandingPlane()) {
            super.handleHitDetection();
            return;
        }

        Vec3 movement = getDeltaMovement();
        if (movement.lengthSqr() < 1.0E-8D) {
            return;
        }

        Vec3 start = position();
        Vec3 end = start.add(movement);
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                level(), this, start, end,
                getBoundingBox().expandTowards(movement).inflate(1.0D), this::canHitEntity);
        if (hitResult != null
                && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hitResult)) {
            onHit(hitResult);
        }
    }

    /** Move normally, but stop exactly at the configured target plane instead of a ceiling block. */
    @Override
    public void travel() {
        Vec3 movement = getDeltaMovement();
        Vec3 start = position();
        Vec3 end = start.add(movement);

        if (hasLandingPlane() && movement.y < 0.0D
                && start.y >= getLandingY() && end.y <= getLandingY()) {
            double fraction = (start.y - getLandingY()) / (start.y - end.y);
            Vec3 landing = start.add(movement.scale(fraction));
            setPos(landing.x, getLandingY(), landing.z);
            if (!level().isClientSide) {
                impactParticles(getX(), getY(), getZ());
                getImpactSound().ifPresent(this::doImpactSound);
            }
            plantAt(landing);
            return;
        }

        if (!hasLandingPlane()) {
            super.travel();
            return;
        }

        setPos(end);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
        if (!isNoGravity()) {
            setDeltaMovement(movement.x, movement.y - 0.05D, movement.z);
        }
    }

    private void applyContactEffect(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)
                || !livingEntity.isAlive()
                || !canHitEntity(entity)) {
            return;
        }

        DamageSources.applyDamage(livingEntity, getDamage(),
                IronsSpellbooksCompatSpells.SWORD_PRISON.get()
                        .getDamageSource(this, getOwner())
                        .setIFrames(0));
        livingEntity.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 8, 1, false, true, true));
        if (isPlanted()) {
            pullTowardSword(livingEntity);
        }
    }

    private void pullTowardSword(LivingEntity livingEntity) {
        Vec3 toSword = position().subtract(livingEntity.getBoundingBox().getCenter());
        Vec3 horizontal = new Vec3(toSword.x, 0.0D, toSword.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            return;
        }

        Vec3 pull = horizontal.normalize().scale(PULL_STRENGTH);
        livingEntity.push(pull.x, 0.0D, pull.z);
    }

    private void damageNearbyEntities() {
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(0.45D),
                candidate -> candidate instanceof LivingEntity && canHitEntity(candidate))) {
            applyContactEffect(entity);
        }
    }

    private void plantAt(Vec3 location) {
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-6D) {
            Vec3 direction = motion.normalize();
            entityData.set(DATA_PLANTED_DIRECTION_X, (float) direction.x);
            entityData.set(DATA_PLANTED_DIRECTION_Y, (float) direction.y);
            entityData.set(DATA_PLANTED_DIRECTION_Z, (float) direction.z);
        }
        setPos(location);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        plantedTicks = 0;
        entityData.set(DATA_PLANTED, true);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Planted", isPlanted());
        tag.putFloat("Roll", getRoll());
        tag.putFloat("PlantedDirectionX", entityData.get(DATA_PLANTED_DIRECTION_X));
        tag.putFloat("PlantedDirectionY", entityData.get(DATA_PLANTED_DIRECTION_Y));
        tag.putFloat("PlantedDirectionZ", entityData.get(DATA_PLANTED_DIRECTION_Z));
        tag.putInt("PlantedTicks", plantedTicks);
        if (hasLandingPlane()) {
            tag.putDouble("LandingY", getLandingY());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boolean planted = tag.getBoolean("Planted");
        entityData.set(DATA_PLANTED, planted);
        entityData.set(DATA_ROLL, tag.getFloat("Roll"));
        if (tag.contains("PlantedDirectionX")) {
            entityData.set(DATA_PLANTED_DIRECTION_X, tag.getFloat("PlantedDirectionX"));
            entityData.set(DATA_PLANTED_DIRECTION_Y, tag.getFloat("PlantedDirectionY"));
            entityData.set(DATA_PLANTED_DIRECTION_Z, tag.getFloat("PlantedDirectionZ"));
        }
        if (tag.contains("LandingY")) {
            entityData.set(DATA_LANDING_Y, (float) tag.getDouble("LandingY"));
        }
        plantedTicks = tag.getInt("PlantedTicks");
        setNoGravity(planted);
        if (planted) {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void trailParticles() {
        Vec3 random = Utils.getRandomVec3(0.025D);
        level().addParticle(ParticleHelper.UNSTABLE_ENDER,
                getX() + random.x, getY() + random.y, getZ() + random.z,
                random.x, random.y, random.z);
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(level(), ParticleHelper.UNSTABLE_ENDER,
                x, y, z, 14, 0.1D, 0.1D, 0.1D, 0.12D, true);
    }

    @Override
    public float getSpeed() {
        return 1.45F;
    }

    @Override
    public Optional<Supplier<SoundEvent>> getImpactSound() {
        return Optional.of(() -> SoundEvents.TRIDENT_HIT);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
