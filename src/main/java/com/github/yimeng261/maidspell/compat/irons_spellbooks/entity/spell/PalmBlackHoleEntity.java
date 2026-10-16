package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.PalmBlackHoleSpell;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A stationary ritual anchor; the renderer and damage front share its synchronized age. */
public class PalmBlackHoleEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(
            PalmBlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> START_HEIGHT = SynchedEntityData.defineId(
            PalmBlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CASTER_ID = SynchedEntityData.defineId(
            PalmBlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RELEASE_YAW = SynchedEntityData.defineId(
            PalmBlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.supernova_black_hole.idle");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> hitTargets = new HashSet<>();
    private UUID casterUuid;

    public PalmBlackHoleEntity(EntityType<? extends PalmBlackHoleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public PalmBlackHoleEntity(Level level, MagicalWinefoxBossEntity caster) {
        this(IronsSpellbooksCompatEntities.PALM_BLACK_HOLE.get(), level);
        casterUuid = caster.getUUID();
        entityData.set(CASTER_ID, caster.getId());
        entityData.set(RELEASE_YAW, caster.yBodyRot);
        Vec3 right = caster.getLookAngle().cross(new Vec3(0, 1, 0)).normalize();
        Vec3 palm = caster.position().add(right.scale(-0.55D)).add(0, 1.5D, 0);
        var hit = level.clip(new ClipContext(palm, new Vec3(palm.x, level.getMinBuildHeight(), palm.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double ground = hit.getType() != HitResult.Type.MISS ? hit.getLocation().y : caster.getY();
        setPos(palm.x, ground + 0.04D, palm.z);
        entityData.set(START_HEIGHT, (float) Math.max(0.5D, palm.y - getY()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        Entity rawCaster = casterUuid == null ? null : server.getEntity(casterUuid);
        if (!(rawCaster instanceof MagicalWinefoxBossEntity caster)
                || !IAuthoritativeHealth.combatAlive(caster) || !caster.isBattleActive()
                || !caster.isPhaseTwo() || caster.isTransitioning()) {
            discard();
            return;
        }
        int age = entityData.get(AGE) + 1;
        // The cast may finish on the same server tick as impact. Only earlier termination cancels it.
        if (age < PalmBlackHoleTimeline.IMPACT_TICK - 1 && (!caster.isCasting()
                || !caster.getMagicData().getCastingSpellId().equals(PalmBlackHoleSpell.SPELL_ID.toString()))) {
            discard();
            return;
        }
        entityData.set(AGE, age);
        entityData.set(CASTER_ID, caster.getId());
        if (age >= PalmBlackHoleTimeline.WARNING_START_TICK && age <= PalmBlackHoleTimeline.TOP_TICK) {
            // Track the projected release point while warning, then freeze it at release.
            // The range marker, detached visual and damage share the same terrain anchor.
            entityData.set(RELEASE_YAW, caster.yBodyRot);
            var offset = PalmBlackHoleLocatorPose.topOffset(caster.yBodyRot);
            Vec3 top = caster.position().add(offset.x, offset.y, offset.z);
            var hit = level().clip(new ClipContext(top, new Vec3(top.x, level().getMinBuildHeight(), top.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            double ground = hit.getType() != HitResult.Type.MISS ? hit.getLocation().y : caster.getY();
            setPos(top.x, ground + 0.04D, top.z);
            entityData.set(START_HEIGHT, (float) Math.max(0, top.y - getY()));
        }
        if (age == PalmBlackHoleTimeline.IMPACT_TICK) {
            server.playSound(null, getX(), getY(), getZ(), MaidSpellSounds.PALM_BLACK_HOLE_EXPLOSION.get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.25D, getZ(),
                    8, 0.5D, 0.1D, 0.5D, 0.03D);
        }
        if (PalmBlackHoleTimeline.isPulseTick(age)) {
            applyPulse(server, caster);
        }
        if (age >= PalmBlackHoleTimeline.IMPACT_TICK) {
            float previous = PalmBlackHoleTimeline.waveRadius(age - 1);
            float radius = PalmBlackHoleTimeline.waveRadius(age);
            for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                    new AABB(position(), position()).inflate(radius + 1, 4, radius + 1))) {
                if (!isEnemy(caster, target) || hitTargets.contains(target.getUUID())) continue;
                Vec3 offset = target.position().subtract(position()).multiply(1, 0, 1);
                double distance = offset.length();
                if (!PalmBlackHoleTimeline.crossedWave(distance, previous, radius)) continue;
                hitTargets.add(target.getUUID());
                boolean damaged = hurtWithVoid(caster, target,
                        PalmBlackHoleTimeline.explosionDamage(maxHealth(target)));
                if (!damaged) continue;
                Vec3 push = distance > 1.0E-5D ? offset.scale(1.1D / distance) : new Vec3(0, 0, 1.1D);
                target.push(push.x, 0.45D, push.z);
                target.hurtMarked = true;
            }
        }
        if (age > PalmBlackHoleTimeline.IMPACT_TICK + PalmBlackHoleTimeline.WAVE_TICKS + 8) discard();
    }

    private void applyPulse(ServerLevel server, LivingEntity caster) {
        float radius = PalmBlackHoleTimeline.PULSE_RADIUS;
        // Pulses cover the landing area while the black hole is above it.
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(position(), position()).inflate(radius))) {
            if (!isEnemy(caster, target)) continue;
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (!PalmBlackHoleTimeline.insidePulse(dx, dz)) continue;
            float halfDamage = PalmBlackHoleTimeline.pulseDamage(maxHealth(target)) * 0.5F;
            boolean damaged = hurtWithVoid(caster, target, halfDamage);
            if (IAuthoritativeHealth.combatAlive(target)) {
                damaged |= hurtTimed(target, halfDamage,
                        IronsSpellbooksCompatSpells.PALM_BLACK_HOLE.get().getDamageSource(this, caster));
            }
            if (damaged) {
                server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        target.getX(), target.getY(0.5D), target.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    private static boolean isEnemy(LivingEntity caster, LivingEntity target) {
        return target != caster && IAuthoritativeHealth.combatAlive(target) && !target.isSpectator()
                && !(target instanceof Player player && player.isCreative())
                && !MaidSpellAllyResolver.areFriendly(caster, target);
    }

    private static float maxHealth(LivingEntity target) {
        return target instanceof IAuthoritativeHealth authority
                ? authority.maidspell$authoritativeMaxHealth() : target.getMaxHealth();
    }

    private boolean hurtWithVoid(LivingEntity caster, LivingEntity target, float damage) {
        DamageSource source = new DamageSource(target.damageSources().fellOutOfWorld().typeHolder(), this, caster);
        return hurtTimed(target, damage, source);
    }

    private boolean hurtTimed(LivingEntity target, float damage, DamageSource source) {
        // Each timed hit is a complete attack, even if another attack's cooldown is still active.
        int previousInvulnerability = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean hurt = false;
        try {
            hurt = DamageSources.applyDamage(target, damage, source);
            return hurt;
        } finally {
            if (!hurt) target.invulnerableTime = previousInvulnerability;
        }
    }

    public float visualAge(float partialTick) {
        return entityData.get(AGE) + partialTick;
    }

    public double visualHeight(float age) {
        return PalmBlackHoleTimeline.dropHeight(age, entityData.get(START_HEIGHT));
    }

    public float releaseYaw() {
        return entityData.get(RELEASE_YAW);
    }

    public boolean belongsTo(LivingEntity caster) {
        return level().isClientSide ? entityData.get(CASTER_ID) == caster.getId() : caster.getUUID().equals(casterUuid);
    }

    @Override protected void defineSynchedData() {
        entityData.define(AGE, 0);
        entityData.define(START_HEIGHT, 1.5F);
        entityData.define(CASTER_ID, -1);
        entityData.define(RELEASE_YAW, 0.0F);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Caster")) casterUuid = tag.getUUID("Caster");
        entityData.set(AGE, tag.getInt("Age"));
        entityData.set(START_HEIGHT, tag.getFloat("StartHeight"));
        entityData.set(RELEASE_YAW, tag.getFloat("ReleaseYaw"));
        for (var value : tag.getList("HitTargets", 10)) {
            CompoundTag hit = (CompoundTag) value;
            if (hit.hasUUID("Id")) hitTargets.add(hit.getUUID("Id"));
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (casterUuid != null) tag.putUUID("Caster", casterUuid);
        tag.putInt("Age", entityData.get(AGE));
        tag.putFloat("StartHeight", entityData.get(START_HEIGHT));
        tag.putFloat("ReleaseYaw", entityData.get(RELEASE_YAW));
        var hits = new net.minecraft.nbt.ListTag();
        for (UUID target : hitTargets) {
            var hit = new CompoundTag();
            hit.putUUID("Id", target);
            hits.add(hit);
        }
        tag.put("HitTargets", hits);
    }

    @Override public AABB getBoundingBoxForCulling() {
        // The model is scaled up at the top and the impact wave spans 16 blocks.
        // Keep the full effect inside the dispatcher frustum while its entity base stays on the ground.
        double top = Math.max(40, entityData.get(START_HEIGHT) + 16);
        return new AABB(position().add(-32, -16, -32), position().add(32, top, 32));
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) {
        // Frustum bounds do not affect Entity's separate distance test. The 0.1-block
        // anchor would otherwise hide the entire effect beyond roughly 6.4 blocks.
        return distance < 128.0D * 128.0D;
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "black_hole", 0, state -> state.setAndContinue(IDLE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
