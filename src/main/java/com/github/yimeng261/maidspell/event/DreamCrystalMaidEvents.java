package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 梦云水晶女仆效果事件监听器
 *
 * 负责：
 * - 生物友好 / Boss 中立（LivingChangeTargetEvent）
 * - 记录女仆攻击 Boss 的历史（LivingHurtEvent）
 * - 特定伤害免疫（LivingAttackEvent）
 * - 复活后无敌（LivingAttackEvent）
 * - 在服务端 tick 中统一处理时停/范围强化到期
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class DreamCrystalMaidEvents {

    /** 记录女仆最近攻击过的 Boss，超时 6000 tick（5 分钟）自动过期 */
    private static final Map<UUID, Map<UUID, Long>> MAID_ATTACKED_BOSSES = new HashMap<>();
    private static final long BOSS_AGGRO_TIMEOUT = 6000L;

    // ========== 怪物仇恨控制 ==========

    /**
     * 所有生物友好，Boss 中立（仅对装备梦云水晶的女仆生效）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof EntityMaid maid)) return;
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) return;

        LivingEntity attacker = event.getEntity();
        boolean isBoss = attacker.getType().is(Tags.EntityTypes.BOSSES);

        if (isBoss) {
            // Boss 中立：只有女仆先攻击才反击
            boolean allowAggro = hasMaidRecentlyAttackedBoss(maid, attacker);
            event.setCanceled(!allowAggro);
        } else {
            // 非 Boss 完全不攻击
            event.setCanceled(true);
        }
    }

    /**
     * 记录女仆攻击了某个 Boss（用于 Boss 中立判定）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidHurtEntity(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof EntityMaid maid)) return;
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) return;
        LivingEntity target = event.getEntity();
        boolean isBoss = target.getType().is(Tags.EntityTypes.BOSSES);

        if (isBoss) {
            recordMaidAttackedBoss(maid, target);
        }
    }

    // ========== 伤害免疫 ==========

    /**
     * 免疫特定伤害类型 + 复活后无敌（最高优先级）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) return;

        DamageSource source = event.getSource();

        ItemStack baubleStack = DreamCatCrystalBauble.findDreamCrystalStack(maid);
        if (baubleStack != null && isInvulnerable(baubleStack)) {
            event.setCanceled(true);
            return;
        }

        // 特定伤害类型免疫
        if (isImmuneTo(source)) {
            event.setCanceled(true);
        }
    }

    // ========== 时停/范围强化调度 ==========

    /**
     * 在服务端 tick 中统一处理到期效果，避免对所有活体逐个轮询
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DreamCatCrystalBauble.processScheduledEffects(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        DreamCatCrystalBauble.clearScheduledEffects();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DreamCatCrystalBauble.clearScheduledEffects();
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

    private static boolean isInvulnerable(ItemStack baubleItem) {
        CompoundTag tag = baubleItem.getOrCreateTag();
        return tag.contains(DreamCatCrystalBauble.NBT_INVULNERABLE_TIME)
            && tag.getInt(DreamCatCrystalBauble.NBT_INVULNERABLE_TIME) > 0;
    }

    private static void recordMaidAttackedBoss(EntityMaid maid, LivingEntity boss) {
        UUID maidUUID = maid.getUUID();
        UUID bossUUID = boss.getUUID();
        long currentTime = maid.level().getGameTime();

        Map<UUID, Long> attackedBosses = MAID_ATTACKED_BOSSES.computeIfAbsent(maidUUID, k -> new HashMap<>());
        attackedBosses.entrySet().removeIf(entry -> currentTime - entry.getValue() >= BOSS_AGGRO_TIMEOUT);
        attackedBosses.put(bossUUID, currentTime);
    }

    private static boolean hasMaidRecentlyAttackedBoss(EntityMaid maid, LivingEntity boss) {
        UUID maidUUID = maid.getUUID();
        UUID bossUUID = boss.getUUID();
        long currentTime = maid.level().getGameTime();
        Map<UUID, Long> bosses = MAID_ATTACKED_BOSSES.get(maidUUID);
        if (bosses == null) {
            return false;
        }
        cleanupExpiredBossAggro(maidUUID, bosses, currentTime);
        Long lastTime = bosses.get(bossUUID);
        return lastTime != null && (currentTime - lastTime) < BOSS_AGGRO_TIMEOUT;
    }

    private static void cleanupExpiredBossAggro(UUID maidUUID, Map<UUID, Long> bosses, long currentTime) {
        bosses.entrySet().removeIf(entry -> currentTime - entry.getValue() >= BOSS_AGGRO_TIMEOUT);
        if (bosses.isEmpty()) {
            MAID_ATTACKED_BOSSES.remove(maidUUID);
        }
    }
}
