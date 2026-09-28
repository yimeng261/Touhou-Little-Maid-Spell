package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.MagicShotgunSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ModifiedMagicMissileEntity extends AbstractMagicProjectile {
    private static final String DAMAGE_SPELL_TAG = "DamageSpell";

    private ResourceLocation damageSpellId;

    @SuppressWarnings("unchecked")
    public ModifiedMagicMissileEntity(Level level, LivingEntity owner, AbstractSpell damageSpell) {
        super((EntityType<? extends Projectile>) (EntityType<?>) EntityRegistry.MAGIC_MISSILE_PROJECTILE.get(), level);
        setOwner(owner);
        setNoGravity(true);
        this.damageSpellId = damageSpell.getSpellResource();
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(level(), ParticleHelper.UNSTABLE_ENDER,
                x, y, z, 25, 0, 0, 0, 0.18D, true);
    }

    @Override
    public float getSpeed() {
        return 2.5F;
    }

    @Override
    public Optional<Holder<SoundEvent>> getImpactSound() {
        return Optional.empty();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = getOwner();
        return super.canHitEntity(entity)
                && (owner == null || !MaidSpellAllyResolver.areFriendly(owner, entity));
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        discard();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        AbstractSpell damageSpell = damageSpellId == null
                ? SpellRegistry.MAGIC_MISSILE_SPELL.get()
                : SpellRegistry.getSpell(damageSpellId);
        var damageSource = damageSpell.getDamageSource(this, getOwner());
        if (MagicShotgunSpell.SPELL_ID.equals(damageSpell.getSpellResource())) {
            damageSource.setIFrames(0);
        }
        DamageSources.applyDamage(hitResult.getEntity(), getDamage(), damageSource);
        discard();
    }

    @Override
    protected void handleEntityHoming() {
        Entity target = getHomingTarget();
        if (!(target instanceof LivingEntity livingTarget) || target.isRemoved()) {
            super.handleEntityHoming();
            return;
        }

        Vec3 wantedPosition = livingTarget.getBoundingBox().getCenter()
                .add(livingTarget.getDeltaMovement());
        double distance = position().distanceTo(wantedPosition);
        float turnStrength = tickCount < 3 ? 0.24F : distance < 5.0D ? 0.72F : 0.42F;
        Vec3 newMotion = homeTowards(wantedPosition, turnStrength);
        if (newMotion.dot(wantedPosition.subtract(position())) < -0.25D && tickCount > 10) {
            stopEntityHoming();
        }
    }

    @Override
    public void trailParticles() {
        Vec3 motion = getDeltaMovement();
        int count = (int) Math.min(20, Math.round(motion.length()) * 3) + 1;
        float step = (float) motion.length() / count;
        for (int i = 0; i < count; i++) {
            Vec3 random = Utils.getRandomVec3(0.02D);
            Vec3 offset = motion.scale(step * i);
            level().addParticle(ParticleHelper.UNSTABLE_ENDER,
                    getX() + random.x + offset.x,
                    getY() + random.y + offset.y,
                    getZ() + random.z + offset.z,
                    random.x, random.y, random.z);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (damageSpellId != null) {
            tag.putString(DAMAGE_SPELL_TAG, damageSpellId.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(DAMAGE_SPELL_TAG)) {
            damageSpellId = ResourceLocation.tryParse(tag.getString(DAMAGE_SPELL_TAG));
        }
    }
}
