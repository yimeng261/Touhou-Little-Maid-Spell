package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ModifiedStarfallCometEntity extends Comet {
    public ModifiedStarfallCometEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    public ModifiedStarfallCometEntity(Level level, LivingEntity owner) {
        this(IronsSpellbooksCompatEntities.MODIFIED_STARFALL_COMET.get(), level);
        setOwner(owner);
    }

    @Override
    protected void onHit(@NotNull HitResult hitResult) {
        if (level().isClientSide) {
            return;
        }

        Vec3 impactPosition = hitResult.getLocation();
        setPos(impactPosition.x, impactPosition.y, impactPosition.z);
        impactParticles(impactPosition.x, impactPosition.y, impactPosition.z);
        getImpactSound().ifPresent(this::doImpactSound);
        float explosionRadius = getExplosionRadius();
        Entity owner = getOwner() == null ? this : getOwner();
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(explosionRadius))) {
            double distanceSqr = entity.getBoundingBox().distanceToSqr(impactPosition);
            if (distanceSqr < explosionRadius * explosionRadius
                    && canHitEntity(entity)
                    && !MaidSpellAllyResolver.areFriendly(owner, entity)) {
                DamageSources.applyDamage(entity, getDamage(),
                        IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get().getDamageSource(this, owner));
            }
        }
        discardHelper(hitResult);
    }
}
