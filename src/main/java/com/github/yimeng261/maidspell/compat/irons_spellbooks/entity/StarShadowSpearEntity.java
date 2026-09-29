package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxSpearImpactEffects;
import com.github.yimeng261.maidspell.mixin.accessor.ThrownTridentAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * 扔出去的星影投枪。
 *
 * <p>玩家投掷时整套飞行逻辑（忠诚回收、穿刺加伤、拾取、落地）都从
 * {@link ThrownTrident} 继承；原版引雷附魔只认三叉戟实体类型，由本类按原版条件补上。
 * 酒狐 Boss 投枪则使用同一个实体类型的专用穿透逻辑，穿过实体后只在方块命中时停留。
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
    }

    /**
     * 原版 {@code ThrownTrident(Level, LivingEntity, ItemStack)} 把 {@code EntityType.TRIDENT}
     * 写死在里面了，这里照着它的构造链补齐拾取物、自定义名、定位、主人与忠诚/光效同步字段。
     */
    public StarShadowSpearEntity(Level level, LivingEntity shooter, ItemStack stack) {
        this(IronsSpellbooksCompatEntities.STAR_SHADOW_SPEAR.get(), level);
        this.setPickupItemStack(stack.copy());
        this.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        if (stack.has(DataComponents.INTANGIBLE_PROJECTILE)) {
            this.pickup = Pickup.CREATIVE_ONLY;
        }
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        // 玩家投掷时由 AbstractArrow.setOwner 把拾取方式放开为 ALLOWED
        this.setOwner(shooter);
        this.entityData.set(ThrownTridentAccessor.maidspell$getLoyaltyData(), this.loyaltyOf(stack));
        this.entityData.set(ThrownTridentAccessor.maidspell$getFoilData(), stack.hasFoil());
    }

    private byte loyaltyOf(ItemStack stack) {
        return this.level() instanceof ServerLevel serverLevel
            ? (byte) Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(serverLevel, stack, this), 0, 127)
            : 0;
    }

    /** 引雷命中生物：原版只在伤害生效且目标不是末影人时触发，与本回调的调用时机一致。 */
    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (this.level() instanceof ServerLevel level && level.isThundering()
            && level.canSeeSky(BlockPos.containing(target.position()))
            && this.hasChanneling(level)) {
            this.strikeChannelingLightning(level, target.position(), target);
        }
    }

    /** 引雷命中避雷针。 */
    @Override
    protected void hitBlockEnchantmentEffects(ServerLevel level, BlockHitResult hitResult, ItemStack stack) {
        super.hitBlockEnchantmentEffects(level, hitResult, stack);
        Vec3 origin = hitResult.getBlockPos().clampLocationWithin(hitResult.getLocation());
        if (level.isThundering() && level.canSeeSky(BlockPos.containing(origin))
            && level.getBlockState(hitResult.getBlockPos()).is(Blocks.LIGHTNING_ROD)
            && this.hasChanneling(level)) {
            this.strikeChannelingLightning(level, origin, this);
        }
    }

    private boolean hasChanneling(ServerLevel level) {
        return EnchantmentHelper.getItemEnchantmentLevel(
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING),
            this.getWeaponItem()) > 0;
    }

    /** 与原版引雷附魔的效果相同：召唤闪电（主人是玩家时记为其所致）并以 5 倍音量播放雷鸣。 */
    private void strikeChannelingLightning(ServerLevel level, Vec3 origin, Entity soundSource) {
        BlockPos pos = BlockPos.containing(origin);
        if (Level.isInSpawnableBounds(pos)) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.spawn(level, pos, MobSpawnType.TRIGGERED);
            if (bolt != null) {
                if (this.getOwner() instanceof ServerPlayer player) {
                    bolt.setCause(player);
                }
                bolt.moveTo(origin.x, origin.y, origin.z, bolt.getYRot(), bolt.getXRot());
            }
        }
        if (!soundSource.isSilent()) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_THUNDER,
                soundSource.getSoundSource(), 5.0F, 1.0F);
        }
    }

    /** 父类默认给的是原版三叉戟，换成星影投枪，免得 /summon 出来的枪被捡起来变成三叉戟。 */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_SPEAR.get());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOSS_PROJECTILE, false);
        builder.define(DATA_BOSS_PROJECTILE_PLANTED, false);
    }

    public void setBossProjectile() {
        this.entityData.set(DATA_BOSS_PROJECTILE, true);
        this.pickup = Pickup.DISALLOWED;
        this.entityData.set(ThrownTridentAccessor.maidspell$getLoyaltyData(), (byte) 0);
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
            if (!this.level().isClientSide && --this.bossProjectilePlantedTicks <= 0) {
                this.discard();
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
                if (!EventHooks.onProjectileImpact(this, entityHit)) {
                    this.onHit(entityHit);
                } else {
                    this.bossHitEntityIds.add(entityHit.getEntity().getId());
                }
                if (this.isRemoved()) {
                    return;
                }
            }

            // 实体命中可穿透，其后第一次方块命中才把枪钉住。
            if (blockHit.getType() == HitResult.Type.BLOCK
                && !EventHooks.onProjectileImpact(this, blockHit)) {
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
            && !MaidSpellAllyResolver.areFriendly(owner, entity)
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
                && entity != this.getOwner() && IAuthoritativeHealth.combatAlive(entity)
                && entity.position().distanceToSqr(impact) <= 9.0D
                && !MaidSpellAllyResolver.areFriendly(this.getOwner(), entity))) {
            nearby.hurt(source, 10.0F);
        }
        level.playSound(null, impact.x, impact.y, impact.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0F, 1.4F);
        WinefoxSpearImpactEffects.spawnEchoBlast(level, impact, 3.0F);
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
