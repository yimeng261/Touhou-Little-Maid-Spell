package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 梦云水晶女仆效果事件监听器
 * <p>
 * 负责：
 * - 生物友好 / Boss 中立（LivingChangeTargetEvent）
 * - 记录女仆攻击 Boss 的历史（LivingDamageEvent.Post）
 * - 特定伤害免疫（LivingIncomingDamageEvent）
 * - 复活后无敌（LivingIncomingDamageEvent）
 * - 解除时停的冻结目标（EntityTickEvent.Post）
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class DreamCrystalMaidEvents {

    /**
     * 记录女仆最近攻击过的 Boss，超时 6000 tick（5 分钟）自动过期
     */
    private static final Map<UUID, Map<UUID, Long>> MAID_ATTACKED_BOSSES = new HashMap<>();
    private static final long BOSS_AGGRO_TIMEOUT = 6000L;

    // ========== 怪物仇恨控制 ==========

    /**
     * 所有生物友好，Boss 中立（仅对装备梦云水晶的女仆生效）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof EntityMaid maid)) return;
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) return;

        LivingEntity attacker = event.getEntity();

        if (attacker.getType().is(Tags.EntityTypes.BOSSES)) {
            // Boss 中立：只有女仆先攻击才反击
            if (!hasMaidRecentlyAttackedBoss(maid, attacker)) {
                event.setCanceled(true);
            }
        } else {
            // 非 Boss 完全不攻击
            event.setCanceled(true);
        }
    }

    /**
     * 记录女仆攻击了某个 Boss（用于 Boss 中立判定）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidHurtEntity(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof EntityMaid maid)) return;
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) return;

        LivingEntity target = event.getEntity();
        if (target.getType().is(Tags.EntityTypes.BOSSES)) {
            recordMaidAttackedBoss(maid, target);
        }
    }

    // ========== 伤害免疫 ==========

    /**
     * 免疫特定伤害类型 + 复活后无敌（最高优先级）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidLivingAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) return;

        DamageSource source = event.getSource();

        // 复活后无敌
        if (DreamCatCrystalBauble.isInvulnerable(maid.getUUID())
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }

        // 特定伤害类型免疫
        if (isImmuneTo(source)) {
            event.setCanceled(true);
        }
    }

    // ========== 时停：解除冻结 ==========

    /**
     * 每 tick 检查是否需要解除被时停的实体
     */
    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide()) return;

        UUID uuid = mob.getUUID();
        Long unfreezeTime = DreamCatCrystalBauble.FROZEN_TARGETS.get(uuid);
        if (unfreezeTime == null) return;

        long currentTime = mob.level().getGameTime();
        if (currentTime >= unfreezeTime) {
            DreamCatCrystalBauble.FROZEN_TARGETS.remove(uuid);
            // 只有在实体仍然存活时才解除冻结
            if (mob.isAlive()) {
                mob.setNoAi(false);
            }
        }
    }

    // ========== 私有辅助方法 ==========

    private static boolean isImmuneTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return source.is(DamageTypeTags.IS_FIRE)         // 燃烧
                || source.is(DamageTypeTags.IS_DROWNING)     // 溺水
                || source.is(DamageTypeTags.IS_EXPLOSION)    // 爆炸
                || source.is(DamageTypes.MAGIC)              // 原版魔法伤害
                || source.is(DamageTypes.LAVA);              // 熔岩
    }

    private static void recordMaidAttackedBoss(EntityMaid maid, LivingEntity boss) {
        MAID_ATTACKED_BOSSES
                .computeIfAbsent(maid.getUUID(), k -> new HashMap<>())
                .put(boss.getUUID(), maid.level().getGameTime());
    }

    private static boolean hasMaidRecentlyAttackedBoss(EntityMaid maid, LivingEntity boss) {
        Map<UUID, Long> bosses = MAID_ATTACKED_BOSSES.get(maid.getUUID());
        if (bosses == null) return false;
        Long lastTime = bosses.get(boss.getUUID());
        if (lastTime == null) return false;
        return (maid.level().getGameTime() - lastTime) < BOSS_AGGRO_TIMEOUT;
    }
}
