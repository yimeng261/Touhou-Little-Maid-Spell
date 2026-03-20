package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.projectiles.AbstractSpellCloud;
import com.Polarice3.Goety.common.entities.projectiles.HellCloud;
import com.Polarice3.Goety.utils.MobUtil;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 晋升之环女仆的狱云追踪逻辑。
 * 仅对由冰雹云转化而来的狱云生效。
 */
@Mixin(AbstractSpellCloud.class)
public abstract class AbstractSpellCloudMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void maidSpell$trackAscensionHellCloud(CallbackInfo ci) {
        AbstractSpellCloud cloud = (AbstractSpellCloud) (Object) this;
        if (!(cloud instanceof HellCloud hellCloud)) {
            return;
        }
        if (hellCloud.level().isClientSide || !hellCloud.isStaff()) {
            return;
        }
        if (!BaubleStateManager.hasMaidWithAscensionHalo(hellCloud.getOwner())) {
            return;
        }

        if (hellCloud.getTarget() == null) {
            for (LivingEntity living : hellCloud.level().getEntitiesOfClass(LivingEntity.class, hellCloud.getBoundingBox().inflate(16.0D))) {
                if (MobUtil.ownedPredicate(hellCloud).test(living)) {
                    cloud.setTarget(living);
                    break;
                }
            }
        }

        LivingEntity target = hellCloud.getTarget();
        float speed = 0.175F;
        if (target != null && target.isAlive()) {
            hellCloud.setDeltaMovement(Vec3.ZERO);

            double dx = target.getX() - hellCloud.getX();
            double dy = target.getY() + 4.0D - hellCloud.getY();
            double dz = target.getZ() - hellCloud.getZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            double totalDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (horizontalDistance > 0.5D) {
                hellCloud.setDeltaMovement(
                    hellCloud.getDeltaMovement()
                        .add(dx / totalDistance, dy / totalDistance, dz / totalDistance)
                        .scale(speed)
                );
            }
        }

        hellCloud.move(MoverType.SELF, hellCloud.getDeltaMovement());
    }
}
