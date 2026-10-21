package com.github.yimeng261.maidspell.mixin.usefulmagic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.usefulmagic.UsefulMagicTerrainProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.coostack.usefulmagic.items.weapon.magic.attack.ExplosionMagic;
import cn.coostack.usefulmagic.utils.FriendFilterHelper;
import cn.coostack.usefulmagic.utils.MagicHelper;

/**
 * 让女仆施放的爆裂魔法真正炸起来。
 *
 * <p>原 {@code ExplosionMagic.onRelease} 开头即 {@code if(!(shooter instanceof Player)) return}，
 * 且后面把施法者强转 {@code ServerPlayer} 做伤害来源，女仆放它蓄满力后什么都不会发生。
 * 这里在 shooter 为女仆时接管：落点沿女仆视线取（与原版同一个 {@code findClipTarget}），
 * 复用原版的爆炸特效与方块爆破，实体伤害按原版半径与衰减重算，伤害来源换成女仆、
 * 对象走友军过滤（见 {@code FriendFilterHelperMixin}），不误伤主人和同队单位。
 * 蓄力期间的玩家专属特效仍不显示。非女仆 shooter 不拦截。
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-10-20
 */
@Mixin(value = ExplosionMagic.class, remap = false)
public abstract class ExplosionMagicMixin {
    /** 与原版 handleExplode 的半径常量一致：e^2 * 1.25 * 3.5。 */
    private static final double MAIDSPELL$EXPLOSION_RADIUS = Math.exp(2.0) * 1.25 * 3.5;
    private static final double MAIDSPELL$ENTITY_DAMAGE_RADIUS = MAIDSPELL$EXPLOSION_RADIUS * 1.8;
    private static final double MAIDSPELL$FULL_DAMAGE_RADIUS = MAIDSPELL$EXPLOSION_RADIUS * 0.75;

    @Shadow
    private void handleExplodeParticle(ServerLevel world, LivingEntity user, Vec3 target) {
    }

    @Shadow
    private void processMeteoriteStyleExplosion(ServerLevel world, LivingEntity user, Vec3 target, int blockExplosionRadius) {
    }

    @Inject(
            method = "onRelease(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void maidspell$explodeForMaid(LivingEntity shooter, Level world, ItemStack wandStack, ItemStack ballStack, int time, CallbackInfo ci) {
        if (!(shooter instanceof EntityMaid maid)) {
            return;
        }
        ci.cancel();
        if (!(world instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 target = MagicHelper.INSTANCE.findClipTarget(serverLevel, maid, 200.0, false, false);
        double damage = MagicHelper.INSTANCE.getMagicDamage(wandStack);
        handleExplodeParticle(serverLevel, maid, target);
        if (!UsefulMagicTerrainProtection.blocksTerrainDamage(maid)) {
            processMeteoriteStyleExplosion(serverLevel, maid, target, Math.max(1, (int) Math.round(MAIDSPELL$EXPLOSION_RADIUS)));
        }

        DamageSource source = serverLevel.damageSources().mobAttack(maid);
        double box = MAIDSPELL$ENTITY_DAMAGE_RADIUS * 2.0;
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(target, box, box, box),
                it -> it != maid && it.isAlive() && FriendFilterHelper.INSTANCE.filterNotFriend(maid, it))) {
            double distance = entity.getBoundingBox().getCenter().distanceTo(target);
            if (distance > MAIDSPELL$ENTITY_DAMAGE_RADIUS) {
                continue;
            }
            double factor = distance <= MAIDSPELL$FULL_DAMAGE_RADIUS ? 1.0
                    : 1.0 - Math.min(1.0, (distance - MAIDSPELL$FULL_DAMAGE_RADIUS) / (MAIDSPELL$ENTITY_DAMAGE_RADIUS - MAIDSPELL$FULL_DAMAGE_RADIUS));
            float finalDamage = (float) (damage * factor);
            if (finalDamage > 0.05f) {
                entity.hurt(source, finalDamage);
            }
        }
    }
}
