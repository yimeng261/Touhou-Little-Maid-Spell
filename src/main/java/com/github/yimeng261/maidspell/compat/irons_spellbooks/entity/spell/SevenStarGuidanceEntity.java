package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;

import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 七星引路的服务端状态机，以服务端为准。 */
public class SevenStarGuidanceEntity extends Entity {
    public static final int STAR_COUNT = 7;
    public static final int FIRST_STRIKE_TICK = 12;
    public static final int STRIKE_INTERVAL = 10;
    public static final int AIM_TICKS = 12;
    public static final int FINAL_TICK = FIRST_STRIKE_TICK + STAR_COUNT * STRIKE_INTERVAL + AIM_TICKS;
    public static final int FINAL_FADE_TICKS = 8;
    public static final int FINALE_DISPLAY_TICKS = 28;
    public static final int FINALE_SHATTER_TICK = FINAL_TICK + FINALE_DISPLAY_TICKS;
    public static final int FINALE_DAMAGE_TICK = FINALE_SHATTER_TICK + 8;
    public static final double SKY_HEIGHT = 9.0D;
    private static final double STRIKE_RADIUS = 2.5D;
    private static final double ARRAY_SCALE = 1.65D;
    private static final double TARGET_SEARCH_RANGE = 32.0D;
    private static final Vec3 HANDLE_TIP = new Vec3(4.0D, 0.0D, -1.5D);
    private static final Vec3[] STAR_OFFSETS = {
            new Vec3(-2.0D, 0.0D, 1.5D),
            new Vec3(-0.5D, 0.0D, 2.0D),
            new Vec3(1.0D, 0.0D, 1.5D),
            new Vec3(1.5D, 0.0D, 0.0D),
            new Vec3(2.5D, 0.0D, -1.0D),
            new Vec3(3.25D, 0.0D, -1.5D),
            HANDLE_TIP
    };
    private static final int[] CONNECTIONS = {0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6};
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID =
            SynchedEntityData.defineId(SevenStarGuidanceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FINALE_STAGE =
            SynchedEntityData.defineId(SevenStarGuidanceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FINALE_MASK =
            SynchedEntityData.defineId(SevenStarGuidanceEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID casterUuid;
    @Nullable
    private UUID targetUuid;
    private float starDamage;
    private float baseFinalDamage;
    private double centerX;
    private double centerY;
    private double centerZ;
    private final Map<UUID, Integer> hitCounts = new HashMap<>();
    private int targetHitMask;
    private boolean shatterDamageApplied;

    public SevenStarGuidanceEntity(EntityType<? extends SevenStarGuidanceEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public SevenStarGuidanceEntity(ServerLevel level, LivingEntity caster, LivingEntity target,
                                   float starDamage, float baseFinalDamage) {
        this(IronsSpellbooksCompatEntities.SEVEN_STAR_GUIDANCE.get(), level);
        casterUuid = caster.getUUID();
        targetUuid = target == null ? null : target.getUUID();
        entityData.set(DATA_TARGET_ID, target == null ? -1 : target.getId());
        this.starDamage = starDamage;
        this.baseFinalDamage = baseFinalDamage;
        centerX = target == null ? caster.getX() : target.getX();
        centerY = target == null ? caster.getY() : target.getY();
        centerZ = target == null ? caster.getZ() : target.getZ();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity caster = getLiving(casterUuid, serverLevel);
        if (!IAuthoritativeHealth.combatAlive(caster) || caster.level() != level()) {
            discard();
            return;
        }

        // 目标可选，但锁定提示和后续追踪需要尽早同步目标 ID；不要等到第一颗星落下才选目标。
        LivingEntity currentTarget = getLiving(targetUuid, serverLevel);
        if (tickCount <= FINAL_TICK && !isEnemy(caster, currentTarget)) {
            setTarget(findNearestTarget(serverLevel, caster));
        }

        if (tickCount >= FIRST_STRIKE_TICK && tickCount < FIRST_STRIKE_TICK + STAR_COUNT * STRIKE_INTERVAL
                && (tickCount - FIRST_STRIKE_TICK) % STRIKE_INTERVAL == 0) {
            strike(serverLevel, caster, (tickCount - FIRST_STRIKE_TICK) / STRIKE_INTERVAL);
        }

        if (tickCount == FINAL_TICK) {
            finish(serverLevel, caster);
        }
        if (tickCount == FINALE_SHATTER_TICK) {
            entityData.set(DATA_FINALE_STAGE, 2);
        }
        if (tickCount == FINALE_DAMAGE_TICK && !shatterDamageApplied) {
            applyShatterDamage(serverLevel, caster);
            shatterDamageApplied = true;
        }
        if (tickCount > FINALE_DAMAGE_TICK + FINAL_FADE_TICKS) {
            discard();
        }
    }

    private void strike(ServerLevel level, LivingEntity caster, int index) {
        if (index < 0 || index >= STAR_COUNT) {
            return;
        }
        LivingEntity lockedTarget = getLiving(targetUuid, level);
        if (!isEnemy(caster, lockedTarget)) {
            lockedTarget = findNearestTarget(level, caster);
            setTarget(lockedTarget);
        }
        Vec3 impactCenter = lockedTarget != null
                ? lockedTarget.getBoundingBox().getCenter()
                : starPoint(index).add(0.0D, -SKY_HEIGHT, 0.0D);
        level.playSound(null, impactCenter.x, impactCenter.y, impactCenter.z,
                MaidSpellSounds.WINEFOX_MAGIC_SHOOT.get(), SoundSource.HOSTILE, 1.0F,
                0.94F + level.random.nextFloat() * 0.12F);
        AABB area = new AABB(impactCenter, impactCenter).inflate(STRIKE_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> isEnemy(caster, entity))) {
            hitCounts.merge(target.getUUID(), 1, Integer::sum);
            if (targetUuid != null && target.getUUID().equals(targetUuid)) {
                targetHitMask |= 1 << index;
            }
            DamageSources.applyDamage(target, starDamage,
                    IronsSpellbooksCompatSpells.SEVEN_STAR_GUIDANCE.get().getDamageSource(this, caster));
            applyStarEffect(index, level, caster, target, impactCenter);
        }
    }

    /** 北斗七星各自附带不同的追加效果；命中计数仍是每次主落击算一次。 */
    private void applyStarEffect(int index, ServerLevel level, LivingEntity caster,
                                 LivingEntity target, Vec3 impactCenter) {
        switch (index) {
            case 0 -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0,
                    false, true, true));
            case 1 -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 1,
                    false, true, true));
            case 2 -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0,
                    false, true, true));
            case 3 -> {
                Vec3 away = target.getBoundingBox().getCenter().subtract(impactCenter)
                        .multiply(1.0D, 0.0D, 1.0D);
                if (away.lengthSqr() < 1.0E-5D) {
                    away = new Vec3(0.0D, 0.0D, 1.0D);
                }
                away = away.normalize().scale(0.55D);
                target.push(away.x, 0.18D, away.z);
                target.hurtMarked = true;
            }
            case 4 -> caster.heal(Math.min(1.5F, caster.getMaxHealth() * 0.02F));
            case 5 -> DamageSources.applyDamage(target, starDamage * 0.25F,
                    IronsSpellbooksCompatSpells.SEVEN_STAR_GUIDANCE.get().getDamageSource(this, caster));
            case 6 -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0,
                    false, true, true));
            default -> {
            }
        }
    }

    private void finish(ServerLevel level, LivingEntity caster) {
        LivingEntity target = getLiving(targetUuid, level);
        if (!isEnemy(caster, target)) {
            target = findNearestTarget(level, caster);
            setTarget(target);
        }
        if (isEnemy(caster, target)) {
            int hits = hitCounts.getOrDefault(target.getUUID(), 0);
            float damage = baseFinalDamage * (1.0F + hits * 0.2F);
            targetHitMask = targetUuid != null && target.getUUID().equals(targetUuid) ? targetHitMask : 0;
            entityData.set(DATA_FINALE_MASK, targetHitMask);
            entityData.set(DATA_FINALE_STAGE, 1);
            level.playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    MaidSpellSounds.WINEFOX_MAGIC_SHOOT.get(), SoundSource.HOSTILE, 1.25F, 0.82F);
            applyFinalEffects(level, caster, target);
            DamageSources.applyDamage(target, damage,
                    IronsSpellbooksCompatSpells.SEVEN_STAR_GUIDANCE.get().getDamageSource(this, caster));
        }
    }

    /** 收束一击以更强的强度重复每颗星的追加效果。 */
    private void applyFinalEffects(ServerLevel level, LivingEntity caster, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 1, false, true, true));
        Vec3 away = target.getBoundingBox().getCenter().subtract(new Vec3(centerX, centerY, centerZ))
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-5D) away = new Vec3(0.0D, 0.0D, 1.0D);
        away = away.normalize().scale(1.0D);
        target.push(away.x, 0.35D, away.z);
        target.hurtMarked = true;
        caster.heal(Math.min(3.0F, caster.getMaxHealth() * 0.03F));
        DamageSources.applyDamage(target, starDamage * 0.7F,
                IronsSpellbooksCompatSpells.SEVEN_STAR_GUIDANCE.get().getDamageSource(this, caster));
    }

    private void applyShatterDamage(ServerLevel level, LivingEntity caster) {
        LivingEntity target = getLiving(targetUuid, level);
        if (!isEnemy(caster, target)) return;
        int hits = hitCounts.getOrDefault(target.getUUID(), 0);
        float damage = baseFinalDamage * (0.75F + hits * 0.1F);
        DamageSources.applyDamage(target, damage,
                IronsSpellbooksCompatSpells.SEVEN_STAR_GUIDANCE.get().getDamageSource(this, caster));
    }

    private Vec3 skyCenter() {
        return new Vec3(centerX, centerY + SKY_HEIGHT, centerZ);
    }

    private Vec3 starPoint(int index) {
        return skyCenter().add(STAR_OFFSETS[index].scale(ARRAY_SCALE));
    }

    /** 客户端渲染用的相对星位；实体本身就是固定的星阵中心。 */
    public Vec3 getRenderStarPoint(int index) {
        return getRenderStarPoint(index, 0.0F);
    }

    public Vec3 getRenderStarPoint(int index, float rotation) {
        Vec3 offset = STAR_OFFSETS[index].scale(ARRAY_SCALE);
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        return new Vec3(offset.x * cos - offset.z * sin, SKY_HEIGHT,
                offset.x * sin + offset.z * cos);
    }

    public int getTargetEntityId() {
        return entityData.get(DATA_TARGET_ID);
    }

    public int getFinaleStage() {
        return entityData.get(DATA_FINALE_STAGE);
    }

    public int getFinaleMask() {
        return entityData.get(DATA_FINALE_MASK);
    }

    private void setTarget(@Nullable LivingEntity target) {
        targetUuid = target == null ? null : target.getUUID();
        entityData.set(DATA_TARGET_ID, target == null ? -1 : target.getId());
    }

    @Nullable
    private LivingEntity findNearestTarget(ServerLevel level, LivingEntity caster) {
        Vec3 center = new Vec3(centerX, centerY, centerZ);
        AABB search = new AABB(center, center).inflate(TARGET_SEARCH_RANGE);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> isEnemy(caster, entity))) {
            double distance = candidate.distanceToSqr(center.x, center.y, center.z);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** 避免头顶的巨大星座及其指向目标的光束被视锥剔除。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        double radius = 32.0D;
        return new AABB(getX() - radius, getY() - 4.0D, getZ() - radius,
                getX() + radius, getY() + SKY_HEIGHT + 8.0D, getZ() + radius);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }

    private static boolean isEnemy(LivingEntity caster, @Nullable LivingEntity target) {
        return target != null && target != caster && IAuthoritativeHealth.combatAlive(target)
                && !target.isSpectator() && !MaidSpellAllyResolver.areFriendly(caster, target);
    }

    @Nullable
    private static LivingEntity getLiving(@Nullable UUID uuid, ServerLevel level) {
        if (uuid == null) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TARGET_ID, -1);
        builder.define(DATA_FINALE_STAGE, 0);
        builder.define(DATA_FINALE_MASK, 0);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (casterUuid != null) tag.putUUID("Caster", casterUuid);
        if (targetUuid != null) tag.putUUID("Target", targetUuid);
        tag.putFloat("StarDamage", starDamage);
        tag.putFloat("BaseFinalDamage", baseFinalDamage);
        tag.putDouble("CenterX", centerX);
        tag.putDouble("CenterY", centerY);
        tag.putDouble("CenterZ", centerZ);
        tag.putInt("TargetHitMask", targetHitMask);
        tag.putInt("FinaleStage", entityData.get(DATA_FINALE_STAGE));
        tag.putBoolean("ShatterDamageApplied", shatterDamageApplied);
        ListTag counts = new ListTag();
        hitCounts.forEach((uuid, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Entity", uuid);
            entry.putInt("Hits", count);
            counts.add(entry);
        });
        tag.put("HitCounts", counts);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.hasUUID("Caster")) casterUuid = tag.getUUID("Caster");
        if (tag.hasUUID("Target")) targetUuid = tag.getUUID("Target");
        starDamage = tag.getFloat("StarDamage");
        baseFinalDamage = tag.getFloat("BaseFinalDamage");
        centerX = tag.getDouble("CenterX");
        centerY = tag.getDouble("CenterY");
        centerZ = tag.getDouble("CenterZ");
        targetHitMask = tag.getInt("TargetHitMask");
        entityData.set(DATA_FINALE_MASK, targetHitMask);
        entityData.set(DATA_FINALE_STAGE, tag.getInt("FinaleStage"));
        shatterDamageApplied = tag.getBoolean("ShatterDamageApplied");
        hitCounts.clear();
        ListTag counts = tag.getList("HitCounts", Tag.TAG_COMPOUND);
        for (Tag raw : counts) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Entity")) hitCounts.put(entry.getUUID("Entity"), entry.getInt("Hits"));
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
