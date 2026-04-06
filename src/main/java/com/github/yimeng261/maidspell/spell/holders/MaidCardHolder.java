package com.github.yimeng261.maidspell.spell.holders;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆符卡持有者
 * 实现LivingCardHolder接口，使女仆能够使用Youkai-Homecoming的符卡系统
 */
public class MaidCardHolder implements LivingCardHolder {
    
    private final EntityMaid maid;
    private final LivingEntity target;
    
    /**
     * 构造函数
     * @param maid 女仆实体
     * @param target 目标实体（可为null）
     */
    public MaidCardHolder(EntityMaid maid, @Nullable LivingEntity target) {
        this.maid = maid;
        this.target = target;
    }
    
    @Override
    public LivingEntity self() {
        return maid;
    }
    
    @Override
    public LivingEntity shooter() {
        return maid;
    }
    
    @Override
    @Nullable
    public LivingEntity targetEntity() {
        return target;
    }
    
    @Override
    public float getDamage(YHDanmaku.IDanmakuType type) {
        // 使用默认伤害值，也可以根据女仆属性调整
        return type.damage();
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
    public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DyeColor color) {
        ItemDanmakuEntity danmaku = new ItemDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), shooter(), maid.level());
        danmaku.setPos(center());
        danmaku.setItem(type.get(color).asStack());
        danmaku.setup(getDamage(type), life, true, true, vec);
        return danmaku;
    }
    
    @Override
    public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
        ItemLaserEntity laser = new ItemLaserEntity(YHEntities.ITEM_LASER.get(), shooter(), maid.level());
        laser.setItem(type.get(color).asStack());
        laser.setup(getDamage(type), life, len, true, vec);
        laser.setPos(pos);
        laser.setupLength = type.setupLength();
        return laser;
    }
    
    @Override
    public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
        ShooterEntity shooter = new ShooterEntity(YHEntities.SHOOTER.get(), maid.level());
        shooter.setup(shooter(), targetEntity(), data, spell);
        return shooter;
    }
    
    @Override
    public void shoot(Entity danmaku) {
        if (maid.level() instanceof ServerLevel sl) {
            sl.addFreshEntity(danmaku);
        }
    }
}

