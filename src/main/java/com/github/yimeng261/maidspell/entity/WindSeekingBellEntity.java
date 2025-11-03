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
        this.setPos(player.getX(), player.getY(), player.getZ());
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
            this.targetY = this.getY() + 20.0D;
        } else {
            this.targetX = d0;
            this.targetY = i;
            this.targetZ = d1;
        }

        this.life = 0;
        this.surviveAfterDeath = true;
    }

    @Override
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide) {
            // 服务器端逻辑
            double dx = this.targetX - this.getX();
            double dz = this.targetZ - this.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance < 1.0) {
                // 到达目标，播放碎裂音效并消失
                this.playBreakSound();
                this.discard();

                return;
            }


            Vec3 vec3 = this.getDeltaMovement();
            double d0 = this.getX() + vec3.x;
            double d2 = this.getZ() + vec3.z;
            double d3 = vec3.horizontalDistance();
            this.setXRot(lerpRotation(this.xRotO, (float)(Mth.atan2(vec3.y, d3) * (double)(180F / (float)Math.PI))));
            this.setYRot(lerpRotation(this.yRotO, (float)(Mth.atan2(vec3.x, vec3.z) * (double)(180F / (float)Math.PI))));
            double d4 = this.targetX - d0;
            double d5 = this.targetZ - d2;
            float f = (float)Math.sqrt(d4 * d4 + d5 * d5);
            float f1 = (float)Mth.atan2(d5, d4);
            double d6 = Mth.lerp(0.0025D, d3, f);
            double d7 = vec3.y;
            if (f < 1.0F) {
                d6 *= 0.8D;
                d7 *= 0.8D;
            }

            int j = this.getY() < this.targetY ? 1 : -1;
            vec3 = new Vec3(Math.cos(f1) * d6, d7 + ((double)j - d7) * (double)0.015F, Math.sin(f1) * d6);
            this.setDeltaMovement(vec3);
            
            HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            this.life++;
            if (this.life > 80 || hitResult.getType() != HitResult.Type.MISS) {
                this.playBreakSound();
                this.discard();
            }
        } else {
            // 客户端粒子效果
            this.spawnCherryParticles();
        }
        
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
    }

    private static float lerpRotation(float p_37274_, float p_37275_) {
        while(p_37275_ - p_37274_ < -180.0F) {
           p_37274_ -= 360.0F;
        }
  
        while(p_37275_ - p_37274_ >= 180.0F) {
           p_37274_ += 360.0F;
        }
  
        return Mth.lerp(0.2F, p_37274_, p_37275_);
     }

    /**
     * 生成樱花粒子效果
     */
    private void spawnCherryParticles() {
        if (this.level().isClientSide) {
            // 在实体周围生成樱花花瓣粒子
            for (int i = 0; i < 4; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * 0.3;
                double offsetY = (this.random.nextDouble() - 0.5) * 0.3;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.3;
                
                this.level().addParticle(
                    ParticleTypes.CHERRY_LEAVES,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    0, -0.1, 0
                );
            }
        }
    }

    /**
     * 播放碎裂音效
     */
    private void playBreakSound() {
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

            if (this.surviveAfterDeath && !currentPlayer.isCreative()) {
                ItemStack item = this.getItem();
                if (!item.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), item);
                    this.level().addFreshEntity(itemEntity);
                }
            }
        } else {
            // 如果找不到玩家，仍然掉落物品（如果设置了surviveAfterDeath）
            if (this.surviveAfterDeath) {
                ItemStack item = this.getItem();
                if (!item.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), item);
                    this.level().addFreshEntity(itemEntity);
                }
            }
        }
    }

    /**
     * 检查是否可以击中实体（避免击中发射者）
     */
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof Player && !entity.isSpectator() && entity.isAlive();
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
        this.surviveAfterDeath = compound.getBoolean("SurviveAfterDeath");
        
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
