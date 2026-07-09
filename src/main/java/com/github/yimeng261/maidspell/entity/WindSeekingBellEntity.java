package com.github.yimeng261.maidspell.entity;

import com.github.yimeng261.maidspell.sound.MaidSpellSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 寻风之铃实体 - 模仿末影之眼的飞行轨迹，但使用樱花粒子和自定义音效
 */
public class WindSeekingBellEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK =
        SynchedEntityData.defineId(WindSeekingBellEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> DATA_TARGET_X =
        SynchedEntityData.defineId(WindSeekingBellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_Y =
        SynchedEntityData.defineId(WindSeekingBellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_Z =
        SynchedEntityData.defineId(WindSeekingBellEntity.class, EntityDataSerializers.FLOAT);

    private double targetX;
    private double targetY;
    private double targetZ;
    private int life;
    private boolean surviveAfterDeath;

    private double aX;
    private double aY;
    private double aZ;

    private Player player;
    private UUID playerUUID;

    public WindSeekingBellEntity(EntityType<? extends WindSeekingBellEntity> entityType, Level level) {
        super(entityType, level);
    }

    public WindSeekingBellEntity(Level level, Player player) {
        this(MaidSpellEntities.WIND_SEEKING_BELL.get(), level);
        this.player = player;
        this.playerUUID = player.getUUID();
        this.setPos(player.getX(), player.getY(0.5), player.getZ());
    }

    public void setItem(ItemStack itemStack) {
        this.getEntityData().set(DATA_ITEM_STACK, itemStack.copy());
    }

    private ItemStack getItemRaw() {
        return this.getEntityData().get(DATA_ITEM_STACK);
    }

    public ItemStack getItem() {
        ItemStack itemStack = this.getItemRaw();
        return itemStack.isEmpty() ? ItemStack.EMPTY : itemStack;
    }

    /**
     * 获取玩家，如果player为null则通过UUID查找
     */
    private Player getPlayer() {
        if (this.player != null && this.player.isAlive()) {
            return this.player;
        }
        
        if (this.playerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            this.player = serverLevel.getPlayerByUUID(this.playerUUID);
            return this.player;
        }
        
        return null;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_ITEM_STACK, ItemStack.EMPTY);
        this.getEntityData().define(DATA_TARGET_X, 0.0F);
        this.getEntityData().define(DATA_TARGET_Y, 0.0F);
        this.getEntityData().define(DATA_TARGET_Z, 0.0F);
    }

    /**
     * 设置目标位置（类似末影之眼的signalTo方法）
     */
    public void signalTo(BlockPos targetPos) {
        this.aX = targetPos.getX() + 0.5;
        this.aY = targetPos.getY();
        this.aZ = targetPos.getZ() + 0.5;
        double d0 = targetPos.getX();
        int i = targetPos.getY();
        double d1 = targetPos.getZ();
        double d2 = d0 - this.getX();
        double d3 = d1 - this.getZ();
        double d4 = Math.sqrt(d2 * d2 + d3 * d3);
        if (d4 > 12.0D) {
            this.targetX = this.getX() + d2 / d4 * 12.0D;
            this.targetZ = this.getZ() + d3 / d4 * 12.0D;
            this.targetY = this.getY() + 8.0D;
        } else {
            this.targetX = d0;
            this.targetY = i;
            this.targetZ = d1;
        }

        this.life = 0;
        this.surviveAfterDeath = true;
        this.syncTargetData();
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            double horizontalDistance = Math.sqrt(x * x + z * z);
            this.setYRot((float) (Mth.atan2(x, z) * 180.0F / (float) Math.PI));
            this.setXRot((float) (Mth.atan2(y, horizontalDistance) * 180.0F / (float) Math.PI));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 vec3 = this.getDeltaMovement();
        double nextX = this.getX() + vec3.x;
        double nextY = this.getY() + vec3.y;
        double nextZ = this.getZ() + vec3.z;
        double horizontalDistance = vec3.horizontalDistance();
        this.setXRot(lerpRotation(this.xRotO, (float) (Mth.atan2(vec3.y, horizontalDistance) * 180.0F / (float) Math.PI)));
        this.setYRot(lerpRotation(this.yRotO, (float) (Mth.atan2(vec3.x, vec3.z) * 180.0F / (float) Math.PI)));

        double syncTargetX = this.getEntityData().get(DATA_TARGET_X);
        double syncTargetY = this.getEntityData().get(DATA_TARGET_Y);
        double syncTargetZ = this.getEntityData().get(DATA_TARGET_Z);
        double dx = syncTargetX - nextX;
        double dz = syncTargetZ - nextZ;
        float targetDistance = (float) Math.sqrt(dx * dx + dz * dz);
        float targetAngle = (float) Mth.atan2(dz, dx);
        double speed = Mth.lerp(0.0025D, horizontalDistance, targetDistance);
        double ySpeed = vec3.y;
        if (targetDistance < 1.0F) {
            speed *= 0.8D;
            ySpeed *= 0.8D;
        }

        int yDirection = this.getY() < syncTargetY ? 1 : -1;
        vec3 = new Vec3(Math.cos(targetAngle) * speed,
                ySpeed + ((double) yDirection - ySpeed) * 0.015F,
                Math.sin(targetAngle) * speed);
        this.setDeltaMovement(vec3);

        if (this.level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                this.level().addParticle(
                        ParticleTypes.CHERRY_LEAVES,
                        nextX - vec3.x * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                        nextY - vec3.y * 0.25 - 0.5,
                        nextZ - vec3.z * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                        vec3.x, vec3.y, vec3.z
                );
            }
        }

        if (!this.level().isClientSide) {
            this.setPos(nextX, nextY, nextZ);
            HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            this.life++;
            if (this.life > 80 || hitResult.getType() != HitResult.Type.MISS) {
                this.onBreak();
                this.discard();
            }
        } else {
            this.setPosRaw(nextX, nextY, nextZ);
        }
    }

    private static float lerpRotation(float current, float target) {
        while (target - current < -180.0F) {
            current -= 360.0F;
        }

        while (target - current >= 180.0F) {
            current += 360.0F;
        }

        return Mth.lerp(0.2F, current, target);
    }

    /**
     * 铃到达终点或碰撞时的处理：音效、玩家效果、掉落物品。
     */
    private void onBreak() {
        this.level().playSound(
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            MaidSpellSounds.WIND_SEEKING_BELL.get(),
            SoundSource.NEUTRAL,
            0.8F,
            0.8F
        );

        Player currentPlayer = this.getPlayer();
        if (currentPlayer != null) {
            currentPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));

            if (currentPlayer.isCreative()) {
                currentPlayer.teleportTo(this.aX, this.aY, this.aZ);
            }
        }

        // 掉落物品（统一处理，不再区分玩家是否存在）
        if (this.surviveAfterDeath && (currentPlayer == null || !currentPlayer.isCreative())) {
            this.dropItem();
        }
    }

    private void dropItem() {
        ItemStack item = this.getItem();
        if (!item.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), item);
            itemEntity.setGlowingTag(true);
            this.level().addFreshEntity(itemEntity);
        }
    }

    /**
     * 检查是否可以击中实体（排除发射者自身）
     */
    protected boolean canHitEntity(Entity entity) {
        if (!(entity instanceof Player) || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }
        // 排除发射者
        if (this.playerUUID != null && entity.getUUID().equals(this.playerUUID)) {
            return false;
        }
        return true;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        ItemStack item = this.getItemRaw();
        if (!item.isEmpty()) {
            compound.put("Item", item.save(new CompoundTag()));
        }
        compound.putDouble("TargetX", this.targetX);
        compound.putDouble("TargetY", this.targetY);
        compound.putDouble("TargetZ", this.targetZ);
        compound.putDouble("AX", this.aX);
        compound.putDouble("AY", this.aY);
        compound.putDouble("AZ", this.aZ);
        compound.putInt("Life", this.life);
        compound.putBoolean("SurviveAfterDeath", this.surviveAfterDeath);
        
        // 保存玩家UUID
        if (this.playerUUID != null) {
            compound.putUUID("PlayerUUID", this.playerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        ItemStack item = ItemStack.of(compound.getCompound("Item"));
        this.setItem(item);
        this.targetX = compound.getDouble("TargetX");
        this.targetY = compound.getDouble("TargetY");
        this.targetZ = compound.getDouble("TargetZ");
        this.aX = compound.getDouble("AX");
        this.aY = compound.getDouble("AY");
        this.aZ = compound.getDouble("AZ");
        this.life = compound.getInt("Life");
        this.surviveAfterDeath = !compound.contains("SurviveAfterDeath") || compound.getBoolean("SurviveAfterDeath");
        this.syncTargetData();
        
        // 加载玩家UUID
        if (compound.hasUUID("PlayerUUID")) {
            this.playerUUID = compound.getUUID("PlayerUUID");
        }
    }

    private void syncTargetData() {
        this.getEntityData().set(DATA_TARGET_X, (float) this.targetX);
        this.getEntityData().set(DATA_TARGET_Y, (float) this.targetY);
        this.getEntityData().set(DATA_TARGET_Z, (float) this.targetZ);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public float getPickRadius() {
        return 0.0F;
    }
}
