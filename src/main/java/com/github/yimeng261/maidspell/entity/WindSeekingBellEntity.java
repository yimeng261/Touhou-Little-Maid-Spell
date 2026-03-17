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
    // 同步给客户端，使客户端能执行完整速度计算，消除外推误差
    private static final EntityDataAccessor<Float> DATA_TARGET_X =
            SynchedEntityData.defineId(WindSeekingBellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_Y =
            SynchedEntityData.defineId(WindSeekingBellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_Z =
            SynchedEntityData.defineId(WindSeekingBellEntity.class, EntityDataSerializers.FLOAT);

    // 服务端精度字段，用于 signalTo 计算后同步
    private double targetX;
    private double targetY;
    private double targetZ;
    private int life;

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
        // 与末影之眼一致：从玩家身体中心高度生成
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM_STACK, ItemStack.EMPTY);
        builder.define(DATA_TARGET_X, 0.0f);
        builder.define(DATA_TARGET_Y, 0.0f);
        builder.define(DATA_TARGET_Z, 0.0f);
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
        // 同步目标坐标给客户端，使客户端能独立计算速度
        this.getEntityData().set(DATA_TARGET_X, (float) this.targetX);
        this.getEntityData().set(DATA_TARGET_Y, (float) this.targetY);
        this.getEntityData().set(DATA_TARGET_Z, (float) this.targetZ);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            double d0 = Math.sqrt(x * x + z * z);
            this.setYRot((float) (Mth.atan2(x, z) * 180.0F / (float) Math.PI));
            this.setXRot((float) (Mth.atan2(y, d0) * 180.0F / (float) Math.PI));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    /**
     * tick — 运动轨迹与末影之眼完全一致，使用 setPos 直接定位（穿透方块），
     * 樱花粒子替代传送门粒子。
     */
    @Override
    public void tick() {
        super.tick();
        Vec3 vec3 = this.getDeltaMovement();
        double d0 = this.getX() + vec3.x;
        double d1 = this.getY() + vec3.y;
        double d2 = this.getZ() + vec3.z;
        double d3 = vec3.horizontalDistance();
        this.setXRot(lerpRotation(this.xRotO, (float) (Mth.atan2(vec3.y, d3) * 180.0F / (float) Math.PI)));
        this.setYRot(lerpRotation(this.yRotO, (float) (Mth.atan2(vec3.x, vec3.z) * 180.0F / (float) Math.PI)));

        // 读取同步目标（客户端和服务端均可执行，消除外推误差）
        double syncTargetX = this.getEntityData().get(DATA_TARGET_X);
        double syncTargetY = this.getEntityData().get(DATA_TARGET_Y);
        double syncTargetZ = this.getEntityData().get(DATA_TARGET_Z);
        {
            double d4 = syncTargetX - d0;
            double d5 = syncTargetZ - d2;
            float f = (float) Math.sqrt(d4 * d4 + d5 * d5);
            float f1 = (float) Mth.atan2(d5, d4);
            double d6 = Mth.lerp(0.0025D, d3, f);
            double d7 = vec3.y;
            if (f < 1.0F) {
                d6 *= 0.8D;
                d7 *= 0.8D;
            }

            int j = this.getY() < syncTargetY ? 1 : -1;
            vec3 = new Vec3(Math.cos(f1) * d6, d7 + ((double) j - d7) * 0.015F, Math.sin(f1) * d6);
            this.setDeltaMovement(vec3);
        }

        // 樱花粒子（客户端，与 EyeOfEnder 粒子位置逻辑一致）
        if (this.level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                this.level().addParticle(
                        ParticleTypes.CHERRY_LEAVES,
                        d0 - vec3.x * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                        d1 - vec3.y * 0.25 - 0.5,
                        d2 - vec3.z * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                        vec3.x, vec3.y, vec3.z
                );
            }
        }

        if (!this.level().isClientSide) {
            this.setPos(d0, d1, d2);
            HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            this.life++;
            if (this.life > 80 || hitResult.getType() != HitResult.Type.MISS) {
                this.onBreak();
                this.discard();
            }
        } else {
            this.setPosRaw(d0, d1, d2);
        }
    }

    protected boolean canHitEntity(Entity entity) {
        if (!(entity instanceof Player) || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }
        if (this.playerUUID != null && entity.getUUID().equals(this.playerUUID)) {
            return false;
        }
        return true;
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
     * 铃到达终点或碰撞时的处理：音效 → 玩家效果 → 掉落物品
     */
    private void onBreak() {
        this.level().playSound(
            null,
                this.getX(), this.getY(), this.getZ(),
            MaidSpellSounds.WIND_SEEKING_BELL.get(),
            SoundSource.NEUTRAL,
                0.8F, 0.8F
        );

        Player currentPlayer = this.getPlayer();
        if (currentPlayer != null) {
            currentPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
            if (currentPlayer.isCreative()) {
                currentPlayer.teleportTo(this.aX, this.aY, this.aZ);
            }
        }

        // 非创造模式掉落物品（带发光描边）
        if (currentPlayer == null || !currentPlayer.isCreative()) {
            dropItem();
        }
    }

    /**
     * 掉落铃物品，带发光描边效果
     */
    private void dropItem() {
        ItemStack item = this.getItem();
        if (!item.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), item);
            itemEntity.setGlowingTag(true);
            this.level().addFreshEntity(itemEntity);
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        ItemStack item = this.getItemRaw();
        if (!item.isEmpty()) {
            compound.put("Item", item.save(this.registryAccess()));
        }
        compound.putDouble("TargetX", this.targetX);
        compound.putDouble("TargetY", this.targetY);
        compound.putDouble("TargetZ", this.targetZ);
        compound.putDouble("AX", this.aX);
        compound.putDouble("AY", this.aY);
        compound.putDouble("AZ", this.aZ);
        compound.putInt("Life", this.life);

        // 保存玩家UUID
        if (this.playerUUID != null) {
            compound.putUUID("PlayerUUID", this.playerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        ItemStack item = ItemStack.parseOptional(this.registryAccess(), compound.getCompound("Item"));
        this.setItem(item);
        this.targetX = compound.getDouble("TargetX");
        this.targetY = compound.getDouble("TargetY");
        this.targetZ = compound.getDouble("TargetZ");
        this.aX = compound.getDouble("AX");
        this.aY = compound.getDouble("AY");
        this.aZ = compound.getDouble("AZ");
        this.life = compound.getInt("Life");

        // 加载玩家UUID
        if (compound.hasUUID("PlayerUUID")) {
            this.playerUUID = compound.getUUID("PlayerUUID");
        }
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
