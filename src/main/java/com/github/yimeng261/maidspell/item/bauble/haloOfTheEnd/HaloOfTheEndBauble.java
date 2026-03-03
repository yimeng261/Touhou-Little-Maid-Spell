package com.github.yimeng261.maidspell.item.bauble.haloOfTheEnd;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 终末之环 (Halo of The-End)
 * 参考：revelationfix/OdamaneHalo
 *
 * 这是一个极其强大的饰品，提供：
 * - 极高的伤害减免（主世界50%、下界75%、末地99%）
 * - 限伤系统（20点 + 最大生命值×25%）
 * - 对弹射物85%伤害减免
 * - 对虚空66.6%伤害减免
 * - 免疫所有负面效果
 * - 免疫多种伤害类型
 * - 维度增益效果
 * - 85%概率免疫死亡
 * - 复活机制（100%生命恢复 + 60秒无敌 + 伤害光环）
 * - 所有生物友好，Boss中立
 */
public class HaloOfTheEndBauble implements IMaidBauble {

    // NBT标签
    private static final String NBT_REVIVE_COOLDOWN = "halo_of_the_end_revive_cooldown";
    private static final String NBT_INVULNERABLE_TIME = "halo_of_the_end_invulnerable_time";
    private static final String NBT_DAMAGE_AURA_TIME = "halo_of_the_end_damage_aura_time";

    // 伤害减免比例
    public static final float OVERWORLD_DAMAGE_REDUCTION = 0.5F;  // 主世界50%
    public static final float NETHER_DAMAGE_REDUCTION = 0.75F;    // 下界75%
    public static final float END_DAMAGE_REDUCTION = 0.99F;       // 末地99%

    // 特殊伤害减免
    public static final float PROJECTILE_DAMAGE_REDUCTION = 0.85F;  // 弹射物85%
    public static final float VOID_DAMAGE_REDUCTION = 0.666F;       // 虚空66.6%

    // 限伤参数
    public static final float DAMAGE_CAP_BASE = 20.0F;              // 基础限伤20点
    public static final float DAMAGE_CAP_HEALTH_RATIO = 0.25F;      // 最大生命值的25%

    // 复活参数
    public static final float DEATH_IMMUNITY_CHANCE = 0.85F;        // 85%概率免疫死亡
    public static final int REVIVAL_INVULNERABILITY_DURATION = 1200; // 60秒无敌（60*20=1200 ticks）
    public static final int REVIVAL_DAMAGE_AURA_DURATION = 12000;   // 600秒伤害光环（600*20=12000 ticks）
    public static final float REVIVAL_DAMAGE_AURA_RANGE = 3.0F;     // 3格范围
    public static final float REVIVAL_DAMAGE_AURA_DAMAGE = 10.0F;   // 每帧10点虚空伤害
    public static final int REVIVE_COOLDOWN_TICKS = 36000;          // 30分钟冷却（1800秒）

    // 无敌帧提升
    public static final int INVULNERABILITY_TICKS = 30;             // 30 ticks

    private static boolean initialized = false;

    /**
     * 初始化终末之环的所有效果
     * 在 MaidBaubleRegistry 中调用
     */
    public static void init() {
        if (initialized) return;

        var haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null) {
            Global.LOGGER.warn("Goety Revelation 未加载，无法初始化终末之环女仆效果");
            return;
        }

        initialized = true;
        Global.LOGGER.info("正在初始化终末之环女仆效果...");

        // ========== 伤害处理已移至 HaloOfTheEndMaidEvents ==========
        // 使用与 revelationfix 相同的事件优先级系统

        // ========== 战斗增强 ==========

        // 法术冷却大幅减少（降至0.1秒 = 2 ticks）
        // 参考：revelationfix 中终末之环的法术冷却降至0.1秒
        Global.baubleCoolDownCalc.put(haloOfTheEnd, (coolDown) -> {
            if (coolDown.maid != null &&
                BaubleStateManager.hasBauble(coolDown.maid, haloOfTheEnd)) {
                coolDown.cooldownticks = 2; // 0.1秒 = 2 ticks
            }
            return null;
        });

        Global.LOGGER.info("终末之环女仆效果初始化完成");
    }

    /**
     * 每tick执行一次
     */
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide) return;

        ResourceKey<Level> dimension = maid.level().dimension();

        // 每秒检查一次（20 ticks）
        if (maid.tickCount % 20 == 0) {
            // 应用维度增益效果
            applyDimensionBuffs(maid, dimension);
        }

        // 处理无敌倒计时
        CompoundTag tag = baubleItem.getOrCreateTag();
        if (tag.contains(NBT_INVULNERABLE_TIME)) {
            int invulnerableTime = tag.getInt(NBT_INVULNERABLE_TIME);
            if (invulnerableTime > 0) {
                tag.putInt(NBT_INVULNERABLE_TIME, invulnerableTime - 1);
                maid.setInvulnerable(true);
            } else {
                tag.remove(NBT_INVULNERABLE_TIME);
                maid.setInvulnerable(false);
            }
        }

        // 处理伤害光环
        if (tag.contains(NBT_DAMAGE_AURA_TIME)) {
            int auraTime = tag.getInt(NBT_DAMAGE_AURA_TIME);
            if (auraTime > 0) {
                tag.putInt(NBT_DAMAGE_AURA_TIME, auraTime - 1);
                applyDamageAura(maid);
            } else {
                tag.remove(NBT_DAMAGE_AURA_TIME);
            }
        }

        // 处理复活冷却
        if (tag.contains(NBT_REVIVE_COOLDOWN)) {
            int cooldown = tag.getInt(NBT_REVIVE_COOLDOWN);
            if (cooldown > 0) {
                tag.putInt(NBT_REVIVE_COOLDOWN, cooldown - 1);
            } else {
                tag.remove(NBT_REVIVE_COOLDOWN);
            }
        }
    }

    /**
     * 应用维度增益效果
     */
    private void applyDimensionBuffs(EntityMaid maid, ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            // 主世界：根据时间应用不同效果
            long dayTime = maid.level().getDayTime() % 24000;
            boolean isDay = dayTime < 12000;

            if (isDay) {
                // 白天：饱和II、抗性提升II、生命恢复II
                maid.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 1, false, false));
                maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
                maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
            } else {
                // 夜晚：夜视III、迅捷III、急迫III、发光III
                maid.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 2, false, false));
                maid.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2, false, false));
                maid.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 2, false, false));
                maid.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 2, false, false));
            }
        } else if (dimension == Level.NETHER) {
            // 下界：力量III
            maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 2, false, false));
            // 注：食尸者和排斥效果需要goety模组支持，这里暂不实现
        } else if (dimension == Level.END) {
            // 末地：对周围实体施加负面效果
            applyEndDebuffs(maid);
        }
    }

    /**
     * 末地：对周围实体施加负面效果
     */
    private void applyEndDebuffs(EntityMaid maid) {
        AABB aabb = maid.getBoundingBox().inflate(6.0);
        List<LivingEntity> entities = maid.level().getEntitiesOfClass(LivingEntity.class, aabb,
            entity -> entity != maid && entity.isAlive());

        for (LivingEntity entity : entities) {
            // 诅咒IV、缓慢IV、虚弱IV
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 3, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 3, false, false));
            // 注：痉挛效果需要goety模组支持
        }
    }

    /**
     * 应用伤害光环（复活后）
     */
    private void applyDamageAura(EntityMaid maid) {
        AABB aabb = maid.getBoundingBox().inflate(REVIVAL_DAMAGE_AURA_RANGE);
        List<LivingEntity> entities = maid.level().getEntitiesOfClass(LivingEntity.class, aabb,
            entity -> entity != maid && entity.isAlive());

        for (LivingEntity entity : entities) {
            // 每帧造成10点虚空伤害
            entity.hurt(maid.damageSources().magic(), REVIVAL_DAMAGE_AURA_DAMAGE);
        }
    }

    /**
     * 获取维度伤害减免比例
     */
    public static float getDimensionDamageReduction(EntityMaid maid) {
        ResourceKey<Level> dimension = maid.level().dimension();

        if (dimension == Level.NETHER) {
            return NETHER_DAMAGE_REDUCTION;
        } else if (dimension == Level.END) {
            return END_DAMAGE_REDUCTION;
        } else {
            return OVERWORLD_DAMAGE_REDUCTION;
        }
    }

    /**
     * 计算限伤后的伤害
     */
    public static float applyDamageCap(float damage, EntityMaid maid) {
        float maxDamage = DAMAGE_CAP_BASE + maid.getMaxHealth() * DAMAGE_CAP_HEALTH_RATIO;
        return Math.min(damage, maxDamage);
    }

    /**
     * 检查是否在复活冷却中
     */
    public static boolean isReviveOnCooldown(ItemStack baubleItem) {
        CompoundTag tag = baubleItem.getOrCreateTag();
        return tag.contains(NBT_REVIVE_COOLDOWN) && tag.getInt(NBT_REVIVE_COOLDOWN) > 0;
    }

    /**
     * 触发复活效果
     */
    public static void triggerRevival(EntityMaid maid, ItemStack baubleItem) {
        CompoundTag tag = baubleItem.getOrCreateTag();

        // 恢复100%生命值
        maid.setHealth(maid.getMaxHealth());

        // 设置60秒无敌
        tag.putInt(NBT_INVULNERABLE_TIME, REVIVAL_INVULNERABILITY_DURATION);

        // 设置600秒伤害光环
        tag.putInt(NBT_DAMAGE_AURA_TIME, REVIVAL_DAMAGE_AURA_DURATION);

        // 设置30分钟复活冷却
        tag.putInt(NBT_REVIVE_COOLDOWN, REVIVE_COOLDOWN_TICKS);

        Global.LOGGER.info("女仆 {} 触发终末之环复活效果", maid.getName().getString());
    }

    /**
     * 检查是否在无敌状态
     */
    public static boolean isInvulnerable(ItemStack baubleItem) {
        CompoundTag tag = baubleItem.getOrCreateTag();
        return tag.contains(NBT_INVULNERABLE_TIME) && tag.getInt(NBT_INVULNERABLE_TIME) > 0;
    }
}
