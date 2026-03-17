package com.github.yimeng261.maidspell.item.bauble.ascensionHalo;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.SpawnParticleMessage;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 晋升之环饰品逻辑
 * 实现女仆版晋升之环的所有效果
 */
public class AscensionHaloBauble implements IMaidBauble {
    
    private static final String NBT_REVIVE_COOLDOWN = "ascension_halo_revive_cooldown";
    private static final String NBT_INVULNERABLE_TIME = "ascension_halo_invulnerable_time";
    private static final int REVIVE_COOLDOWN_TICKS = 36000; // 30分钟 (1800秒)
    private static final int INVULNERABLE_DURATION = 600; // 30秒
    private static boolean initialized = false;
    
    /**
     * 初始化晋升之环的所有效果
     * 在 MaidBaubleRegistry 中调用
     */
    public static void init() {
        if (initialized) return;
        
        var ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo == null) {
            Global.LOGGER.warn("Goety Revelation 未加载，无法初始化晋升之环女仆效果");
            return;
        }
        
        initialized = true;
        Global.LOGGER.info("正在初始化晋升之环女仆效果...");
        
        // ========== 伤害处理已移至 AscensionHaloMaidEvents ==========
        // 使用与 revelationfix 相同的事件优先级系统：
        // - MobEffectEvent.Applicable (HIGHEST) - 药水效果免疫
        // - LivingAttackEvent (HIGHEST) - 完全免疫
        // - LivingDamageEvent (HIGHEST) - 特殊减免
        // - LivingDamageEvent (LOWEST) - 维度减伤和限伤

        // ========== 战斗增强 ==========

        // 法术冷却减半
        Global.baubleCooldownHandlers.put(ascensionHalo, (coolDown) -> {
            if (coolDown.maid != null && 
                BaubleStateManager.hasBauble(coolDown.maid, ascensionHalo)) {
                coolDown.cooldownticks = coolDown.cooldownticks / 2; // 冷却减半
            }
            return null;
        });
        
        Global.LOGGER.info("晋升之环女仆效果初始化完成");
    }
    
    /**
     * 每tick执行一次
     * 处理：维度特殊效果、无敌倒计时、复活后范围伤害
     * 参考：OdamanePlayerExpandedContext#baseTick
     */
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide) return;

        ResourceKey<Level> dimension = maid.level().dimension();

        // 每秒检查一次（20 ticks）
        if (maid.tickCount % 20 == 0) {
            // ========== 维度特殊 Buff 效果（与原版 OdamanePlayerExpandedContext#baseTick 一致）==========

            if (dimension == Level.END) {
                // 末地：对周围 6 格实体施加负面效果（诅咒、痉挛、缓慢、虚弱 IV）
                applyEndDebuffs(maid);
            } else if (dimension == Level.NETHER) {
                // 下界：给予女仆食尸者、力量、排斥、生命恢复效果（全效果 IV 级）
                applyNetherBuffs(maid);
            } else if (dimension == Level.OVERWORLD) {
                // 主世界：根据昼夜给予不同效果
                applyOverworldBuffs(maid);
            }
        }

        // 处理无敌倒计时
        CompoundTag tag = baubleItem.getOrCreateTag();
        if (tag.contains(NBT_INVULNERABLE_TIME)) {
            int invulTime = tag.getInt(NBT_INVULNERABLE_TIME);
            if (invulTime > 0) {
                tag.putInt(NBT_INVULNERABLE_TIME, invulTime - 1);

                // 复活后无敌期间对周围实体造成持续伤害（与原版一致）
                applyReviveInvulnerableDamage(maid);

                // 无敌期间的视觉效果（每10 ticks一次粒子）
                if (invulTime % 10 == 0) {
                    NetworkHandler.sendToNearby(maid, new SpawnParticleMessage(
                        maid.getId(), SpawnParticleMessage.Type.HEART));
                }
            } else {
                tag.remove(NBT_INVULNERABLE_TIME);
            }
        }
    }

    /**
     * 末地效果：对周围 6 格实体施加负面效果
     * 参考：OdamanePlayerExpandedContext#baseTick (END dimension)
     */
    private void applyEndDebuffs(EntityMaid maid) {
        if (maid.tickCount % 5 != 0) return;

        for (LivingEntity living : maid.level().getEntitiesOfClass(
            LivingEntity.class,
            maid.getBoundingBox().inflate(6.0),
            entity -> entity != maid && !entity.isSpectator())) {

            // 施加 Goety 效果
            living.addEffect(new MobEffectInstance(com.Polarice3.Goety.common.effects.GoetyEffects.CURSED.get(), 200, 3));
            living.addEffect(new MobEffectInstance(com.Polarice3.Goety.common.effects.GoetyEffects.SPASMS.get(), 200, 3));
            // 施加原版效果
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 3));
        }
    }

    /**
     * 下界效果：给予女仆食尸者、力量、排斥、生命恢复效果
     * 参考：OdamanePlayerExpandedContext#baseTick (NETHER dimension)
     */
    private void applyNetherBuffs(EntityMaid maid) {
        maid.addEffect(new MobEffectInstance(com.Polarice3.Goety.common.effects.GoetyEffects.CORPSE_EATER.get(), 200, 3, false, false));
        maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 3, false, false));
        maid.addEffect(new MobEffectInstance(com.Polarice3.Goety.common.effects.GoetyEffects.REPULSIVE.get(), 200, 3, false, false));
        maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false));
    }

    /**
     * 主世界效果：根据昼夜给予不同效果
     * 参考：OdamanePlayerExpandedContext#baseTick (OVERWORLD dimension)
     */
    private void applyOverworldBuffs(EntityMaid maid) {
        long dayTime = maid.level().getDayTime() % 24000L;

        if (dayTime < 12000L) {
            // 白天：饱和、抗性提升、生命恢复效果（全效果 II 级）
            maid.addEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 1, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false));
        } else {
            // 夜晚：夜视、迅捷、急迫、发光效果（夜视 III 级，其他 II-III 级）
            maid.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1360, 2, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 2, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 2, false, false));
        }
    }

    /**
     * 复活后无敌期间对周围实体造成持续伤害
     * 参考：OdamanePlayerExpandedContext#baseTick (reviveInvulnerableTime > MAX_REVIVE_INVULNERABLE_TIME - 60 * 20)
     */
    private void applyReviveInvulnerableDamage(EntityMaid maid) {
        for (LivingEntity living : maid.level().getEntitiesOfClass(
            LivingEntity.class,
            maid.getBoundingBox().inflate(3.0),
            entity -> entity != maid && !entity.isSpectator() && entity.isAlive())) {

            // 跳过女仆的主人
            if (living.getUUID().equals(maid.getOwnerUUID())) {
                continue;
            }

            // 减少无敌时间以确保能造成伤害
            if (living.invulnerableTime > 0) {
                living.invulnerableTime--;
            }

            // 造成虚空伤害（10 点）
            DamageSource damageSource = maid.damageSources().fellOutOfWorld();
            living.hurt(damageSource, 10.0F);
        }
    }
    
    /**
     * 女仆死亡时触发
     * 实现复活机制（与原版 AbilityEvents#onOdamanePlayerDeath 一致）
     * <p>
     * 原版有两次复活机会：
     * 1. 第一次：85% 概率触发，恢复到 1 HP + 治疗 7 HP，获得抗性提升 126 级 40 ticks
     * 2. 第二次：如果冷却结束，恢复到 75% 最大生命值，获得 30 秒无敌
     */
    @Override
    public boolean onDeath(EntityMaid maid, ItemStack baubleItem, DamageSource source) {
        // 检查是否可以触发复活
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false; // 无法绕过无敌的伤害（如/kill）无法触发复活
        }

        // ========== 第一次复活机会：85% 概率 ==========
        if (maid.getRandom().nextFloat() < 0.85F) {
            maid.setHealth(1.0F);
            maid.heal(7.0F);
            maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 126));

            // 播放复活音效和粒子
            maid.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);
            NetworkHandler.sendToNearby(maid, new SpawnParticleMessage(
                maid.getId(), SpawnParticleMessage.Type.HEART));

            Global.LOGGER.info("女仆 {} 触发晋升之环第一次复活（85%概率），恢复至 8 生命值",
                maid.getCustomName() != null ? maid.getCustomName().getString() : "未命名");

            return true; // 取消死亡
        }

        // ========== 第二次复活机会：检查冷却 ==========
        long currentTime = maid.level().getGameTime();
        CompoundTag tag = baubleItem.getOrCreateTag();
        long lastReviveTime = tag.getLong(NBT_REVIVE_COOLDOWN);

        if (currentTime - lastReviveTime < REVIVE_COOLDOWN_TICKS) {
            // 冷却中，无法复活
            return false;
        }

        // 触发第二次复活
        tag.putLong(NBT_REVIVE_COOLDOWN, currentTime);

        // 恢复 75% 生命值（与原版一致）
        float healAmount = maid.getMaxHealth() * 0.75F;
        maid.setHealth(healAmount);

        // 设置 30 秒无敌
        tag.putInt(NBT_INVULNERABLE_TIME, INVULNERABLE_DURATION);

        // 播放复活音效和粒子
        maid.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);
        NetworkHandler.sendToNearby(maid, new SpawnParticleMessage(
            maid.getId(), SpawnParticleMessage.Type.HEART));

        // 尝试召唤凋灵仆从（如果加载了Goety模组）
        if (maid.level() instanceof ServerLevel serverLevel) {
            summonWitherMinion(serverLevel, maid);
        }

        Global.LOGGER.info("女仆 {} 触发晋升之环第二次复活（冷却结束），恢复至 {} 生命值",
            maid.getCustomName() != null ? maid.getCustomName().getString() : "未命名",
            healAmount);

        return true; // 取消死亡
    }

    
    /**
     * 召唤凋灵仆从辅助战斗
     * 参考原版：复活时召唤凋灵骷髅或其他仆从
     */
    private void summonWitherMinion(ServerLevel level, EntityMaid maid) {
        // 创建凋灵骷髅仆从
        com.Polarice3.Goety.common.entities.ally.undead.skeleton.WitherSkeletonServant witherSkeleton =
            com.Polarice3.Goety.common.entities.ModEntityType.WITHER_SKELETON_SERVANT.get().create(level);

        if (witherSkeleton == null) {
            Global.LOGGER.warn("晋升之环复活：无法创建凋灵骷髅仆从实体");
            return;
        }

        // 设置仆从的位置
        witherSkeleton.moveTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), 0.0F);

        // 设置仆从的主人为女仆的主人
        if (maid.getOwnerUUID() != null) {
            LivingEntity owner = level.getPlayerByUUID(maid.getOwnerUUID());
            if (owner != null) {
                witherSkeleton.setTrueOwner(owner);
            }
        }

        // 将实体添加到世界
        level.addFreshEntity(witherSkeleton);

        Global.LOGGER.info("晋升之环复活：成功召唤凋灵骷髅仆从");
    }
}
