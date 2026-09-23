package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;

public class StarShadowStrikeEntity extends AoeEntity {
    private static final EntityDataAccessor<Boolean> DATA_MIRRORED =
            SynchedEntityData.defineId(StarShadowStrikeEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int TICKS_PER_FRAME = 2;
    public static final int FRAME_COUNT = 4;

    public StarShadowStrikeEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    public StarShadowStrikeEntity(Level level, boolean mirrored) {
        this(IronsSpellbooksCompatEntities.STAR_SHADOW_STRIKE.get(), level);
        entityData.set(DATA_MIRRORED, mirrored);
    }

    @Override
    public void applyEffect(LivingEntity target) {
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= TICKS_PER_FRAME * FRAME_COUNT) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MIRRORED, false);
    }

    public boolean isMirrored() {
        return entityData.get(DATA_MIRRORED);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void refreshDimensions() {
    }

    @Override
    public void ambientParticles() {
    }

    @Override
    public float getParticleCount() {
        return 0.0F;
    }

    @Override
    public Optional<ParticleOptions> getParticle() {
        return Optional.empty();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
