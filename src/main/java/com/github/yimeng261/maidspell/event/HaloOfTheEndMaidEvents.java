package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.haloOfTheEnd.HaloOfTheEndBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 终末之环女仆效果事件监听器
 * 参考 revelationfix 的 HaloEvents 和 AbilityEvents 实现
 *
 * 终末之环比晋升之环更强大，提供：
 * - 更高的伤害减免（主世界50%、下界75%、末地99%）
 * - 限伤系统（20点 + 最大生命值×25%）
 * - 对弹射物85%伤害减免
 * - 对虚空66.6%伤害减免
 * - 免疫所有负面效果
 * - 免疫多种伤害类型
 * - 85%概率免疫死亡
 * - 复活机制
 */
@Mod.EventBusSubscriber
public class HaloOfTheEndMaidEvents {

    /**
     * 记录女仆最近攻击过的 Boss（用于 Boss 中立判定）
     */
    private static final Map<UUID, Map<UUID, Long>> MAID_ATTACKED_BOSSES = new HashMap<>();
    private static final long BOSS_AGGRO_TIMEOUT = 6000; // 5分钟

    /**
     * 药水效果免疫（最高优先级）
     * 终末之环：免疫所有负面效果
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null || !BaubleStateManager.hasBauble(maid, haloOfTheEnd)) {
            return;
        }

        MobEffect effect = event.getEffectInstance().getEffect();
        MobEffectCategory category = effect.getCategory();

        // 免疫所有负面和中性效果
        if (category == MobEffectCategory.HARMFUL ||
            category == MobEffectCategory.NEUTRAL) {
            // 特殊保留：夜视效果
            if (effect != MobEffects.NIGHT_VISION) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    /**
     * 怪物仇恨控制（最高优先级）
     * 终末之环：所有生物友好，Boss中立
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof EntityMaid maid)) {
            return;
        }

        Item haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null || !BaubleStateManager.hasBauble(maid, haloOfTheEnd)) {
            return;
        }

        LivingEntity attacker = event.getEntity();

        // 检查是否为 Boss
        if (attacker.getType().is(Tags.EntityTypes.BOSSES)) {
            // Boss 中立：只有女仆先攻击过才会反击
            boolean maidAttackedThisBoss = hasMaidRecentlyAttackedBoss(maid, attacker);
            event.setCanceled(!maidAttackedThisBoss);
        } else {
            // 非 Boss 完全不攻击
            event.setCanceled(true);
        }
    }

    /**
     * 记录女仆攻击 Boss 的事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidHurtEntity(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null || !BaubleStateManager.hasBauble(maid, haloOfTheEnd)) {
            return;
        }

        LivingEntity target = event.getEntity();

        // 如果攻击的是 Boss，记录下来
        if (target.getType().is(Tags.EntityTypes.BOSSES)) {
            recordMaidAttackedBoss(maid, target);
        }
    }

    /**
     * 处理伤害免疫（最高优先级）
     * 终末之环：免疫火焰、熔岩、爆炸、溺水、摔落、窒息、挤压、魔法伤害
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null || !BaubleStateManager.hasBauble(maid, haloOfTheEnd)) {
            return;
        }

        DamageSource source = event.getSource();

        // 检查是否在无敌状态
        ItemStack baubleItem = BaubleStateManager.getBaubleItem(maid, haloOfTheEnd);
        if (baubleItem != null && HaloOfTheEndBauble.isInvulnerable(baubleItem)) {
            event.setCanceled(true);
            return;
        }

        // 免疫多种伤害类型
        if (source.is(DamageTypes.IN_FIRE) ||
            source.is(DamageTypes.ON_FIRE) ||
            source.is(DamageTypes.LAVA) ||
            source.is(DamageTypes.HOT_FLOOR) ||
            source.is(DamageTypes.EXPLOSION) ||
            source.is(DamageTypes.PLAYER_EXPLOSION) ||
            source.is(DamageTypes.DROWN) ||
            source.is(DamageTypes.FALL) ||
            source.is(DamageTypes.FLY_INTO_WALL) ||
            source.is(DamageTypes.IN_WALL) ||
            source.is(DamageTypes.CRAMMING) ||
            source.is(DamageTypes.MAGIC) ||
            source.is(DamageTypes.INDIRECT_MAGIC)) {
            event.setCanceled(true);
        }
    }

    /**
     * 处理特殊伤害减免（高优先级）
     * 终末之环：弹射物85%减免、虚空66.6%减免
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamageHigh(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null || !BaubleStateManager.hasBauble(maid, haloOfTheEnd)) {
            return;
        }

        DamageSource source = event.getSource();
        float damage = event.getAmount();

        // 弹射物伤害减免85%
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            damage *= (1.0F - HaloOfTheEndBauble.PROJECTILE_DAMAGE_REDUCTION);
        }

        // 虚空伤害减免66.6%
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) {
            damage *= (1.0F - HaloOfTheEndBauble.VOID_DAMAGE_REDUCTION);
        }

        event.setAmount(damage);
    }

    /**
     * 处理维度减伤和限伤（最低优先级）
     * 终末之环：主世界50%、下界75%、末地99%减伤 + 限伤系统
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamageLow(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null || !BaubleStateManager.hasBauble(maid, haloOfTheEnd)) {
            return;
        }

        float damage = event.getAmount();

        // 应用维度减伤
        float dimensionReduction = HaloOfTheEndBauble.getDimensionDamageReduction(maid);
        damage *= (1.0F - dimensionReduction);

        // 应用限伤
        damage = HaloOfTheEndBauble.applyDamageCap(damage, maid);

        event.setAmount(damage);
    }

    /**
     * 处理死亡事件（最高优先级）
     * 终末之环：85%概率免疫死亡 + 复活机制
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        if (haloOfTheEnd == null || !BaubleStateManager.hasBauble(maid, haloOfTheEnd)) {
            return;
        }

        ItemStack baubleItem = BaubleStateManager.getBaubleItem(maid, haloOfTheEnd);
        if (baubleItem == null) {
            return;
        }

        // 检查是否在复活冷却中
        if (HaloOfTheEndBauble.isReviveOnCooldown(baubleItem)) {
            return;
        }

        // 85%概率免疫死亡
        if (maid.getRandom().nextFloat() < HaloOfTheEndBauble.DEATH_IMMUNITY_CHANCE) {
            event.setCanceled(true);
            // 触发复活效果
            HaloOfTheEndBauble.triggerRevival(maid, baubleItem);
        }
    }

    // ========== 辅助方法 ==========

    private static boolean hasMaidRecentlyAttackedBoss(EntityMaid maid, LivingEntity boss) {
        Map<UUID, Long> attackedBosses = MAID_ATTACKED_BOSSES.get(maid.getUUID());
        if (attackedBosses == null) {
            return false;
        }

        Long lastAttackTime = attackedBosses.get(boss.getUUID());
        if (lastAttackTime == null) {
            return false;
        }

        // 检查是否在超时时间内
        long currentTime = maid.level().getGameTime();
        return (currentTime - lastAttackTime) < BOSS_AGGRO_TIMEOUT;
    }

    private static void recordMaidAttackedBoss(EntityMaid maid, LivingEntity boss) {
        MAID_ATTACKED_BOSSES.computeIfAbsent(maid.getUUID(), k -> new HashMap<>())
            .put(boss.getUUID(), maid.level().getGameTime());
    }
}
