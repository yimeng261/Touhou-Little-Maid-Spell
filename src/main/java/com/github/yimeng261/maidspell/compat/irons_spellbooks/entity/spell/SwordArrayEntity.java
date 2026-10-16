package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import io.redspace.ironsspellbooks.api.entity.NoKnockbackProjectile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Optional;

/** One tracked entity per cast; individual swords are immutable trajectories, not world entities. */
public class SwordArrayEntity extends Projectile implements NoKnockbackProjectile, AntiMagicSusceptible {
    private static final EntityDataAccessor<CompoundTag> DATA_LAYOUT =
            SynchedEntityData.defineId(SwordArrayEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> DATA_REMOVED =
            SynchedEntityData.defineId(SwordArrayEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private SwordArrayLayout layout;
    private long startedAt;
    private float damage;
    private BitSet removed = new BitSet();
    private final BitSet soundedRings = new BitSet();
    private WinefoxSwordProjectileEntity contactProxy;
    private int lastVisualAge = -1;

    public SwordArrayEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public SwordArrayEntity(ServerLevel level, LivingEntity caster, LivingEntity target,
                            int spellLevel, float damage) {
        this(IronsSpellbooksCompatEntities.SWORD_ARRAY.get(), level);
        setOwner(caster);
        Vec3 center = target.getBoundingBox().getCenter();
        setPos(center.x, target.getY(), center.z);
        this.damage = damage;
        CompoundTag descriptor = new CompoundTag();
        descriptor.putInt("SpellLevel", spellLevel);
        descriptor.putDouble("Radius", SwordArrayLayout.innerRadius(target.getBbWidth() * 0.5D));
        descriptor.putLong("Seed", level.getRandom().nextLong());
        descriptor.putLong("StartedAt", level.getGameTime());
        entityData.set(DATA_LAYOUT, descriptor);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_LAYOUT, new CompoundTag());
        entityData.define(DATA_REMOVED, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LAYOUT.equals(key)) {
            CompoundTag descriptor = entityData.get(DATA_LAYOUT);
            if (descriptor.contains("SpellLevel") || descriptor.contains("Rings")) {
                int spellLevel = descriptor.contains("SpellLevel") ? descriptor.getInt("SpellLevel")
                        : Math.max(1, descriptor.getInt("Rings") - 1);
                layout = new SwordArrayLayout(spellLevel, descriptor.getDouble("Radius"),
                        descriptor.getLong("Seed"));
                startedAt = descriptor.getLong("StartedAt");
                lastVisualAge = getArrayAge();
            }
        } else if (DATA_REMOVED.equals(key)) {
            removed = BitSet.valueOf(entityData.get(DATA_REMOVED).getLongArray("Swords"));
        }
    }

    public SwordArrayLayout getLayout() {
        return layout;
    }

    public int getArrayAge() {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, level().getGameTime() - startedAt));
    }

    public boolean isSwordRemoved(int index) {
        return removed.get(index);
    }

    @Override
    public void tick() {
        super.tick();
        if (layout == null) {
            if (!level().isClientSide) discard();
            return;
        }
        int age = getArrayAge();
        if (level().isClientSide) {
            tickVisuals(age);
            return;
        }
        tickContacts(age);
        if (age >= layout.lifetime()) discard();
    }

    private WinefoxSwordProjectileEntity contactProxy() {
        if (contactProxy == null || contactProxy.isRemoved()) {
            contactProxy = new WinefoxSwordProjectileEntity(
                    IronsSpellbooksCompatEntities.WINEFOX_SWORD_PROJECTILE.get(), level());
            contactProxy.setOwner(getOwner());
            contactProxy.setDamage(damage);
        }
        return contactProxy;
    }

    private void tickContacts(int age) {
        AABB activeBounds = null;
        for (int i = 0; i < layout.swords().size(); i++) {
            if (removed.get(i)) continue;
            SwordArrayLayout.Sword sword = layout.swords().get(i);
            AABB box = null;
            if (sword.flying(age)) {
                box = SwordArrayLayout.swordBox(sword.position(age - 1).add(position()))
                        .expandTowards(sword.velocity(age - 1)).inflate(1.0D);
            } else if (sword.dealsContactDamage(age)) {
                box = SwordArrayLayout.swordBox(sword.position(age).add(position()))
                        .inflate(SwordArrayLayout.CONTACT_INFLATION);
            }
            if (box != null) activeBounds = activeBounds == null ? box : activeBounds.minmax(box);
        }
        if (activeBounds == null) return;

        SwordArrayCollisionIndex<Entity> index = new SwordArrayCollisionIndex<>(activeBounds);
        // One world query for all flying and planted swords that need a collision check this tick.
        for (Entity candidate : level().getEntities(this, activeBounds, entity -> !entity.isSpectator())) {
            index.add(candidate, candidate.getBoundingBox().inflate(0.3F));
        }
        for (int i = 0; i < layout.swords().size(); i++) {
            if (removed.get(i)) continue;
            SwordArrayLayout.Sword sword = layout.swords().get(i);
            if (sword.flying(age)) hitFlyingSword(index, sword, age);
            else if (sword.dealsContactDamage(age)) hitPlantedSword(index, sword, age);
            else continue;
            if (contactProxy != null && contactProxy.isRemoved()) removeSword(i);

            if (age == sword.landingAge() && !soundedRings.get(sword.ring())) {
                soundedRings.set(sword.ring());
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT,
                        SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    private void hitFlyingSword(SwordArrayCollisionIndex<Entity> index, SwordArrayLayout.Sword sword, int age) {
        Vec3 start = sword.position(age - 1).add(position());
        // Keep the original full flight segment for hit detection, including the landing tick.
        Vec3 movement = sword.velocity(age - 1);
        Vec3 end = start.add(movement);
        AABB search = SwordArrayLayout.swordBox(start).expandTowards(movement).inflate(1.0D);
        WinefoxSwordProjectileEntity proxy = contactProxy();
        proxy.prepareArrayContact(start, movement, false);
        EntityHitResult nearest = null;
        double distance = Double.MAX_VALUE;
        for (Entity candidate : index.query(search)) {
            if (!candidate.getBoundingBox().intersects(search) || !proxy.canArrayHit(candidate)) continue;
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3F).clip(start, end);
            if (hit.isPresent()) {
                double hitDistance = start.distanceToSqr(hit.get());
                if (hitDistance < distance) {
                    distance = hitDistance;
                    nearest = new EntityHitResult(candidate);
                }
            }
        }
        if (nearest != null && !ForgeEventFactory.onProjectileImpact(proxy, nearest)) {
            proxy.hitArrayFlight(nearest);
        }
    }

    private void hitPlantedSword(SwordArrayCollisionIndex<Entity> index, SwordArrayLayout.Sword sword, int age) {
        Vec3 point = sword.position(age).add(position());
        AABB box = SwordArrayLayout.swordBox(point).inflate(SwordArrayLayout.CONTACT_INFLATION);
        WinefoxSwordProjectileEntity proxy = contactProxy();
        proxy.prepareArrayContact(point, Vec3.ZERO, true);
        for (Entity candidate : index.query(box)) {
            if (candidate instanceof LivingEntity && candidate.getBoundingBox().intersects(box)) {
                proxy.applyArrayContact(candidate);
                if (proxy.isRemoved()) break;
            }
        }
    }

    private void removeSword(int sword) {
        removed.set(sword);
        CompoundTag state = new CompoundTag();
        state.putLongArray("Swords", removed.toLongArray());
        entityData.set(DATA_REMOVED, state);
    }

    private void tickVisuals(int age) {
        if (age == lastVisualAge) return;
        int previous = lastVisualAge;
        lastVisualAge = age;
        if (level().getNearestPlayer(this, 48.0D) == null) return;
        int impactBudget = 24;
        int trailBudget = 8;
        for (int step = 0; step < layout.swords().size(); step++) {
            int i = (step + age * 7) % layout.swords().size();
            if (removed.get(i)) continue;
            SwordArrayLayout.Sword sword = layout.swords().get(i);
            Vec3 point = sword.position(age).add(position());
            // Late trackers start at the current age and never replay old landing bursts.
            if (previous < sword.landingAge() && age >= sword.landingAge() && impactBudget >= 2) {
                for (int particle = 0; particle < 2; particle++) {
                    level().addParticle(ParticleHelper.UNSTABLE_ENDER, point.x, point.y, point.z,
                            random.nextGaussian() * 0.08D, random.nextDouble() * 0.12D,
                            random.nextGaussian() * 0.08D);
                }
                impactBudget -= 2;
            }
            if (trailBudget > 0 && sword.visible(age) && (i + age) % 6 == 0
                    && (age < sword.landingAge() || age % 3 == 0)) {
                level().addParticle(ParticleHelper.UNSTABLE_ENDER, point.x, point.y, point.z, 0, 0.015D, 0);
                trailBudget--;
            }
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return layout == null ? super.getBoundingBoxForCulling() : layout.bounds().inflate(1.5D).move(position());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = layout == null ? 96.0D : 96.0D + layout.bounds().getXsize() * 0.5D;
        return distance < range * range;
    }

    @Override
    public void onAntiMagic(MagicData magicData) {
        discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Layout", entityData.get(DATA_LAYOUT).copy());
        tag.put("RemovedSwords", entityData.get(DATA_REMOVED).copy());
        tag.putFloat("Damage", damage);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        entityData.set(DATA_LAYOUT, tag.getCompound("Layout"));
        entityData.set(DATA_REMOVED, tag.getCompound("RemovedSwords"));
    }
}
