package com.github.yimeng261.maidspell.entity;

import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxSpearImpactEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.ModList;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * 扔出去的星影投枪。
 *
 * <p>玩家投掷时整套飞行逻辑（忠诚回收、引雷、穿刺加伤、拾取、落地）都从
 * {@link ThrownTrident} 继承；酒狐 Boss 投枪则使用同一个实体类型的专用穿透逻辑，
 * 穿过实体后只在方块命中时停留。
 */
public class StarShadowSpearEntity extends ThrownTrident implements GeoEntity {

    private static final EntityDataAccessor<Boolean> DATA_BOSS_PROJECTILE =
        SynchedEntityData.defineId(StarShadowSpearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BOSS_PROJECTILE_PLANTED =
        SynchedEntityData.defineId(StarShadowSpearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int MAX_BOSS_ENTITY_HITS_PER_TICK = 128;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int bossProjectilePlantedTicks;
    private final Set<Integer> bossHitEntityIds = new HashSet<>();

    public StarShadowSpearEntity(EntityType<? extends ThrownTrident> entityType, Level level) {
        super(entityType, level);
        // 父类默认塞的是原版三叉戟，换成自己人，免得 /summon 出来的枪被捡起来变成三叉戟。
        this.tridentItem = new ItemStack(MaidSpellItems.STAR_SHADOW_SPEAR.get());
    }

    /**
     * 原版 {@code ThrownTrident(Level, LivingEntity, ItemStack)} 把 {@code EntityType.TRIDENT}
     * 写死在里面了，用不上，只能照着 {@code AbstractArrow(EntityType, LivingEntity, Level)}
     * 的构造链把定位、主人、拾取方式补齐。
     */
    public StarShadowSpearEntity(Level level, LivingEntity shooter, ItemStack stack) {
        this(MaidSpellEntities.STAR_SHADOW_SPEAR.get(), level);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        this.setOwner(shooter);
        if (shooter instanceof Player) {
            this.pickup = Pickup.ALLOWED;
        }
        this.tridentItem = stack.copy();
        this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(stack));
        this.entityData.set(ID_FOIL, stack.hasFoil());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_BOSS_PROJECTILE, false);
        this.entityData.define(DATA_BOSS_PROJECTILE_PLANTED, false);
    }

    public void setBossProjectile() {
        this.entityData.set(DATA_BOSS_PROJECTILE, true);
        this.pickup = Pickup.DISALLOWED;
        this.entityData.set(ID_LOYALTY, (byte) 0);
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        if (!this.isBossProjectile()) {
            super.tick();
            return;
        }
        if (this.isBossProjectilePlanted()) {
            this.baseTick();
            if (!this.level().isClientSide) {
                if (--this.bossProjectilePlantedTicks <= 0) {
                    this.discard();
                }
            }
            return;
        }
        this.tickBossFlight();
        if (!this.level().isClientSide && this.tickCount >= 100) {
            this.discard();
        }
    }

    private void tickBossFlight() {
        this.baseTick();
        Vec3 movement = this.getDeltaMovement();
        if (movement.lengthSqr() <= 1.0E-8D) {
            return;
        }

        Vec3 start = this.position();
        Vec3 end = start.add(movement);
        BlockHitResult blockHit = this.level().clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        if (!this.level().isClientSide) {
            AABB searchBox = this.getBoundingBox().expandTowards(movement).inflate(1.0D);
            int entityHits = 0;
            EntityHitResult entityHit;
            while (entityHits++ < MAX_BOSS_ENTITY_HITS_PER_TICK
                && (entityHit = ProjectileUtil.getEntityHitResult(
                    this.level(), this, start, entityEnd, searchBox, this::canHitEntity)) != null) {
                if (!ForgeEventFactory.onProjectileImpact(this, entityHit)) {
                    this.onHit(entityHit);
                } else {
                    this.bossHitEntityIds.add(entityHit.getEntity().getId());
                }
                if (this.isRemoved()) {
                    return;
                }
            }

            // Entity hits are piercing. The first block hit after them is the one that plants the spear.
            if (blockHit.getType() == HitResult.Type.BLOCK
                && !ForgeEventFactory.onProjectileImpact(this, blockHit)) {
                this.onHit(blockHit);
                return;
            }
        }

        this.setPos(end);
        this.updateRotation();
        this.checkInsideBlocks();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!this.isBossProjectile()) {
            return super.canHitEntity(entity);
        }
        Entity owner = this.getOwner();
        return entity.canBeHitByProjectile()
            && (owner == null || entity.getRootVehicle() != owner.getRootVehicle())
            && (owner == null || !MaidSpellAllyResolver.areFriendly(owner, entity))
            && !this.bossHitEntityIds.contains(entity.getId());
    }

    @Override
    protected void onHit(HitResult result) {
        if (!this.isBossProjectile()) {
            super.onHit(result);
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity directTarget = result instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (directTarget != null && !this.bossHitEntityIds.add(directTarget.getId())) {
            return;
        }
        var impact = result.getLocation();
        var source = this.damageSources().indirectMagic(this, this.getOwner());
        if (directTarget != null) {
            directTarget.hurt(source, 20.0F);
        }
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
            new AABB(impact, impact).inflate(3.0D), entity -> entity != directTarget
                && entity != this.getOwner() && entity.isAlive()
                && entity.position().distanceToSqr(impact) <= 9.0D
                && (this.getOwner() == null || !MaidSpellAllyResolver.areFriendly(this.getOwner(), entity)))) {
            nearby.hurt(source, 10.0F);
        }
        level.playSound(null, impact.x, impact.y, impact.z,
            net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
            net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.4F);
        if (ModList.get().isLoaded("irons_spellbooks")) {
            WinefoxSpearImpactEffects.spawnEchoBlast(level, impact, 3.0F);
        }
        if (directTarget == null) {
            this.setPos(impact.x, impact.y, impact.z);
            this.setNoGravity(true);
            this.entityData.set(DATA_BOSS_PROJECTILE_PLANTED, true);
            this.bossProjectilePlantedTicks = 60;
        }
    }

    private boolean isBossProjectile() {
        return this.entityData.get(DATA_BOSS_PROJECTILE);
    }

    private boolean isBossProjectilePlanted() {
        return this.entityData.get(DATA_BOSS_PROJECTILE_PLANTED);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WinefoxBossProjectile", this.isBossProjectile());
        tag.putBoolean("WinefoxBossProjectilePlanted", this.isBossProjectilePlanted());
        tag.putInt("WinefoxBossProjectilePlantedTicks", this.bossProjectilePlantedTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("WinefoxBossProjectile")) {
            this.setBossProjectile();
            this.entityData.set(DATA_BOSS_PROJECTILE_PLANTED, tag.getBoolean("WinefoxBossProjectilePlanted"));
            this.bossProjectilePlantedTicks = tag.getInt("WinefoxBossProjectilePlantedTicks");
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
