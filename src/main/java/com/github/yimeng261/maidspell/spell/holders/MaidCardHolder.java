package com.github.yimeng261.maidspell.spell.holders;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.xkmc.danmakuapi.api.DanmakuBullet;
import dev.xkmc.danmakuapi.api.DanmakuLaser;
import dev.xkmc.danmakuapi.content.entity.ItemBulletEntity;
import dev.xkmc.danmakuapi.content.entity.ItemLaserEntity;
import dev.xkmc.danmakuapi.content.spell.spellcard.CardHolder;
import dev.xkmc.danmakuapi.init.registrate.DanmakuEntities;
import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.spell.mover.AttachedMover;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 女仆符卡持有者
 * 实现 danmaku_api 的 CardHolder，使女仆能够使用妖怪们的归家的符卡系统
 */
public class MaidCardHolder implements CardHolder {

    private final EntityMaid maid;
    private final LivingEntity target;
    private final List<SimplifiedProjectile> cache;

    /**
     * @param maid 女仆实体
     * @param target 目标实体（可为null）
     * @param cache 本次符卡已发射的弹幕，用于让附着型激光跟随女仆朝向
     */
    public MaidCardHolder(EntityMaid maid, @Nullable LivingEntity target, List<SimplifiedProjectile> cache) {
        this.maid = maid;
        this.target = target;
        this.cache = cache;
    }

    @Override
    public LivingEntity self() {
        return maid;
    }

    @Nullable
    public LivingEntity targetEntity() {
        return target;
    }

    public float getDamage(int baseDamage) {
        // 使用默认伤害值，也可以根据女仆属性调整
        return baseDamage;
    }

    @Override
    public Vec3 center() {
        return maid.position().add(0, maid.getBbHeight() / 2, 0);
    }

    @Override
    public Vec3 forward() {
        var targetPos = target();
        if (targetPos != null) {
            return targetPos.subtract(center()).normalize();
        }
        return maid.getForward();
    }

    @Override
    @Nullable
    public Vec3 target() {
        if (target == null) return null;
        return target.position().add(0, target.getBbHeight() / 2, 0);
    }

    @Override
    @Nullable
    public Vec3 targetVelocity() {
        if (target == null) return null;
        return target.getDeltaMovement();
    }

    @Override
    public RandomSource random() {
        return maid.getRandom();
    }

    @Override
    public ItemBulletEntity prepareDanmaku(int life, Vec3 vec, DanmakuBullet type, DyeColor color) {
        ItemBulletEntity danmaku = new ItemBulletEntity(DanmakuEntities.ITEM_DANMAKU.get(), maid, maid.level());
        danmaku.setPos(center());
        danmaku.setItem(type.get(color).asStack());
        danmaku.setup(getDamage(type.damage()), life, true, true, vec);
        return danmaku;
    }

    @Override
    public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, DanmakuLaser type, DyeColor color) {
        ItemLaserEntity laser = new ItemLaserEntity(DanmakuEntities.ITEM_LASER.get(), maid, maid.level());
        laser.setItem(type.get(color).asStack());
        laser.setup(getDamage(type.damage()), life, len, true, vec);
        laser.setPos(pos);
        laser.setupLength = type.setupLength();
        return laser;
    }

    @Override
    public void shoot(SimplifiedProjectile danmaku) {
        if (danmaku instanceof ItemBulletEntity itemDanmaku && itemDanmaku.afterExpiry != null) {
            itemDanmaku.afterExpiry.setup(this);
        }
        if (danmaku instanceof ItemLaserEntity laser && laser.mover instanceof AttachedMover) {
            alignLaserToForward(laser);
        }
        cache.add(danmaku);
        if (maid.level() instanceof ServerLevel sl) {
            EntityStorageHelper.fastAdd(sl, danmaku);
        }
    }

    public void updateCachedProjectiles() {
        cache.removeIf(projectile -> projectile == null || !projectile.isValid());
        for (SimplifiedProjectile projectile : cache) {
            if (projectile instanceof ItemLaserEntity laser && laser.mover instanceof AttachedMover) {
                alignLaserToForward(laser);
            }
        }
    }

    private void alignLaserToForward(ItemLaserEntity laser) {
        laser.setup(laser.damage, 1, laser.length, true, forward());
        laser.setupTime(20, 1, 1, 1);
    }
}
