package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.client.particles.CircleExplodeParticleOption;
import com.Polarice3.Goety.client.particles.DustCloudParticleOption;
import com.Polarice3.Goety.client.particles.ModParticleTypes;
import com.Polarice3.Goety.common.entities.projectiles.HellBolt;
import com.Polarice3.Goety.common.entities.projectiles.Hellfire;
import com.Polarice3.Goety.common.entities.projectiles.WaterHurtingProjectile;
import com.Polarice3.Goety.init.ModSounds;
import com.Polarice3.Goety.utils.BlockFinder;
import com.Polarice3.Goety.utils.ColorUtil;
import com.Polarice3.Goety.utils.ServerParticleUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mega.revelationfix.common.entity.TheEndHellfire;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆终末之环：HellBolt 击中时生成 TheEndHellfire
 * 参考 revelationfix 的 HellBoltMixin
 */
@Mixin(HellBolt.class)
public abstract class HellBoltMixin extends WaterHurtingProjectile {

    HellBoltMixin(EntityType<? extends AbstractHurtingProjectile> p_36833_, Level p_36834_) {
        super(p_36833_, p_36834_);
    }

    @Shadow(remap = false)
    public abstract boolean isRain();

    /**
     * 检查所有者是否为装备终末之环的女仆
     */
    @Unique
    private boolean maidSpell$isMaidWithHaloOfTheEnd() {
        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity livingOwner)) {
            return false;
        }
        return BaubleStateManager.hasMaidWithHaloOfTheEnd(livingOwner);
    }

    @Inject(method = "onHit", at = @At(value = "INVOKE", target = "Lcom/Polarice3/Goety/common/entities/projectiles/WaterHurtingProjectile;onHit(Lnet/minecraft/world/phys/HitResult;)V", shift = At.Shift.AFTER), cancellable = true)
    private void maidSpell$onHit(HitResult pResult, CallbackInfo ci) {
        Level level = this.level();
        if (!level.isClientSide && maidSpell$isMaidWithHaloOfTheEnd()) {
            ci.cancel();

            if (!this.isRain()) {
                Vec3 vec3 = Vec3.atCenterOf(this.blockPosition());
                Entity ownerEntity = this.getOwner();
                if (!(ownerEntity instanceof EntityMaid maid)) {
                    return;
                }

                if (pResult instanceof BlockHitResult blockHitResult) {
                    BlockPos blockpos = blockHitResult.getBlockPos().relative(blockHitResult.getDirection());
                    if (BlockFinder.canBeReplaced(level, blockpos)) {
                        // 所有者设置为女仆本身
                        Hellfire hellfire = new TheEndHellfire(level, Vec3.atCenterOf(blockpos), maid);
                        vec3 = Vec3.atCenterOf(blockpos);
                        level.addFreshEntity(hellfire);
                    }
                } else if (pResult instanceof EntityHitResult entityHitResult) {
                    Entity entity1 = entityHitResult.getEntity();
                    // 所有者设置为女仆本身
                    Hellfire hellfire = new TheEndHellfire(level, Vec3.atCenterOf(entity1.blockPosition()), maid);
                    vec3 = Vec3.atCenterOf(entity1.blockPosition());
                    level.addFreshEntity(hellfire);
                }

                if (level instanceof ServerLevel serverLevel) {
                    ServerParticleUtil.addParticlesAroundSelf(serverLevel, ModParticleTypes.BIG_FIRE.get(), this);
                    ColorUtil colorUtil = new ColorUtil(14523414);
                    serverLevel.sendParticles(new CircleExplodeParticleOption(colorUtil.red, colorUtil.green, colorUtil.blue, 2.0F, 1), vec3.x, BlockFinder.moveDownToGround(this), vec3.z, 1, 0.0, 0.0, 0.0, 0.0);
                    DustCloudParticleOption cloudParticleOptions = new DustCloudParticleOption(new Vector3f(Vec3.fromRGB24(8021604).toVector3f()), 1.0F);
                    DustCloudParticleOption cloudParticleOptions2 = new DustCloudParticleOption(new Vector3f(Vec3.fromRGB24(15508116).toVector3f()), 1.0F);

                    for (int i = 0; i < 2; ++i) {
                        ServerParticleUtil.circularParticles(serverLevel, cloudParticleOptions, vec3.x, this.getY() + 0.25, vec3.z, 0.0, 0.14, 0.0, 1.0F);
                    }

                    ServerParticleUtil.circularParticles(serverLevel, cloudParticleOptions2, vec3.x, this.getY() + 0.25, vec3.z, 0.0, 0.14, 0.0, 1.0F);
                }

                this.playSound(ModSounds.HELL_BOLT_IMPACT.get(), 1.0F, 1.0F);
            }

            this.discard();
        }
    }
}
