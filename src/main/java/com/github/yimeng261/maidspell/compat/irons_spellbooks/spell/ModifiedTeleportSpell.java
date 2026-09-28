package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedMagicMissileEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.network.particles.TeleportParticlesPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Optional;

public class ModifiedTeleportSpell extends AbstractSpell implements BossExclusiveSpell {
    public static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "teleport_modified");
    private static final int TARGET_RANGE = 24;
    private static final int SAFE_SEARCH_RADIUS = 3;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(4)
            .setCooldownSeconds(10)
            .build();

    public ModifiedTeleportSpell() {
        baseManaCost = 30;
        manaCostPerLevel = 10;
        baseSpellPower = 8;
        spellPowerPerLevel = 2;
        castTime = 0;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ENDERMAN_TELEPORT);
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return AnimationHolder.none();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        return Utils.preCastTargetHelper(level, caster, magicData, this, TARGET_RANGE, 0.2F,
                true, target -> target != caster && !MaidSpellAllyResolver.areFriendly(caster, target));
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getMissileDamage(spellLevel, caster), 2)),
                Component.translatable("ui.irons_spellbooks.projectile_count", getMissileCount(spellLevel)),
                Component.translatable("ui.irons_spellbooks.distance", TARGET_RANGE));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (level.isClientSide
                || !(magicData.getAdditionalCastData() instanceof TargetEntityCastData targetData)) {
            super.onCast(level, spellLevel, caster, castSource, magicData);
            return;
        }

        LivingEntity target = targetData.getTarget((ServerLevel) level);
        if (IAuthoritativeHealth.combatAlive(target)
                && !MaidSpellAllyResolver.areFriendly(caster, target)) {
            Vec3 destination = findTeleportDestination(level, caster, target);
            if (destination != null) {
                spawnHomingMissiles(level, spellLevel, caster, target);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                        new TeleportParticlesPacket(caster.position(), destination));
                if (caster.isPassenger()) {
                    caster.stopRiding();
                }
                Utils.handleSpellTeleport(this, caster, destination);
                caster.resetFallDistance();
                faceTarget(caster, target);
                caster.playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.0F);
            }
        }

        magicData.resetAdditionalCastData();
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    private void spawnHomingMissiles(Level level, int spellLevel,
                                     LivingEntity caster, LivingEntity target) {
        int count = getMissileCount(spellLevel);
        float damage = getMissileDamage(spellLevel, caster);
        Vec3 spawnCenter = caster.getEyePosition().add(0.0D, -0.15D, 0.0D);
        Vec3 axis = target.getBoundingBox().getCenter().subtract(spawnCenter).normalize();
        Vec3 firstBasis = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (firstBasis.lengthSqr() < 1.0E-4D) {
            firstBasis = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        firstBasis = firstBasis.normalize();
        Vec3 secondBasis = axis.cross(firstBasis).normalize();

        for (int i = 0; i < count; i++) {
            double angle = Mth.TWO_PI * i / count;
            Vec3 initialDirection = firstBasis.scale(Math.cos(angle))
                    .add(secondBasis.scale(Math.sin(angle))).normalize();

            ModifiedMagicMissileEntity missile = new ModifiedMagicMissileEntity(level, caster, this);
            missile.setPos(spawnCenter.add(initialDirection.scale(0.45D)));
            missile.shoot(initialDirection);
            missile.setDamage(damage);
            missile.setHomingTarget(target);
            level.addFreshEntity(missile);
        }
    }

    private Vec3 findTeleportDestination(Level level, LivingEntity caster, LivingEntity target) {
        Vec3 origin = caster.position();
        Vec3 targetPosition = target.position();
        Vec3 desired = targetPosition.scale(2.0D).subtract(origin);
        Vec3 travel = desired.subtract(origin);
        if (travel.lengthSqr() < 1.0E-4D) {
            return null;
        }

        double halfHeight = caster.getBbHeight() * 0.5D;
        Vec3 rayStart = origin.add(0.0D, halfHeight, 0.0D);
        Vec3 rayEnd = desired.add(0.0D, halfHeight, 0.0D);
        BlockHitResult obstruction = level.clip(new ClipContext(rayStart, rayEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 candidate = desired;
        if (obstruction.getType() == HitResult.Type.BLOCK) {
            double clearance = Math.max(0.65D, caster.getBbWidth() * 0.75D);
            candidate = obstruction.getLocation()
                    .subtract(travel.normalize().scale(clearance))
                    .add(0.0D, -halfHeight, 0.0D);
        }

        return findSafeLanding(level, caster, candidate);
    }

    private Vec3 findSafeLanding(Level level, LivingEntity caster, Vec3 preferred) {
        BlockPos preferredFeet = BlockPos.containing(preferred);
        int baseFloorY = Mth.floor(preferred.y - 0.01D);
        int[] verticalOffsets = {0, -1, 1, -2, 2, -3, 3};

        for (int radius = 0; radius <= SAFE_SEARCH_RADIUS; radius++) {
            for (int verticalOffset : verticalOffsets) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (radius > 0 && Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                            continue;
                        }
                        BlockPos floorPos = new BlockPos(preferredFeet.getX() + xOffset,
                                baseFloorY + verticalOffset, preferredFeet.getZ() + zOffset);
                        Vec3 landing = getSafeLandingOn(level, caster, floorPos);
                        if (landing != null && isPathClear(level, caster, landing)) {
                            return landing;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Vec3 getSafeLandingOn(Level level, LivingEntity caster, BlockPos floorPos) {
        if (!level.getBlockState(floorPos).entityCanStandOn(level, floorPos, caster)) {
            return null;
        }
        VoxelShape floorShape = level.getBlockState(floorPos).getCollisionShape(level, floorPos);
        if (floorShape.isEmpty()) {
            return null;
        }

        double floorTop = floorPos.getY() + floorShape.max(Direction.Axis.Y);
        Vec3 landing = new Vec3(floorPos.getX() + 0.5D, floorTop + 0.001D,
                floorPos.getZ() + 0.5D);
        BlockPos feetPos = BlockPos.containing(landing);
        if (level.getFluidState(feetPos).is(FluidTags.LAVA)) {
            return null;
        }

        AABB destinationBox = caster.getBoundingBox().move(landing.subtract(caster.position()));
        return level.noCollision(caster, destinationBox) ? landing : null;
    }

    private boolean isPathClear(Level level, LivingEntity caster, Vec3 landing) {
        Vec3 startLow = caster.position().add(0.0D, 0.1D, 0.0D);
        Vec3 endLow = landing.add(0.0D, 0.1D, 0.0D);
        Vec3 startHigh = caster.position().add(0.0D, caster.getBbHeight() - 0.1D, 0.0D);
        Vec3 endHigh = landing.add(0.0D, caster.getBbHeight() - 0.1D, 0.0D);
        return isRayClear(level, caster, startLow, endLow)
                && isRayClear(level, caster, startHigh, endHigh);
    }

    private boolean isRayClear(Level level, LivingEntity caster, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getType() == HitResult.Type.MISS;
    }

    private void faceTarget(LivingEntity caster, LivingEntity target) {
        Vec3 direction = target.getEyePosition().subtract(caster.getEyePosition());
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
        caster.setYRot(yaw);
        caster.setXRot(pitch);
        caster.setYHeadRot(yaw);
        caster.yBodyRot = yaw;
        if (caster instanceof ServerPlayer player) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        }
    }

    public int getMissileCount(int spellLevel) {
        return Mth.clamp(spellLevel + 2, 3, 6);
    }

    public float getMissileDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.35F;
    }

    /** 超类的具体方法优先于接口默认方法，必须显式覆写。 */
    @Override
    public boolean allowCrafting() {
        return false;
    }

    @Override
    public boolean allowLooting() {
        return false;
    }
}
